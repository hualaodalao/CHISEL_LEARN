# Qwen3.5-2B 逐层计算拆解

2026-10-27 ｜ 依据 `config.json`、权重清单（632 个张量）、`transformers` 的 `modeling_qwen3_5.py` 与 fla kernel 源码核对

## 摘要

Qwen3.5-2B 是视觉语言模型，总参数 2,274.07 M：文本主干 1,881.8 M、视觉塔 331.4 M、MTP 头 60.8 M。文本主干 24 层，按 3:1 交替放置 18 层 Gated DeltaNet 和 6 层 Gated Attention，每层都挂一份 SwiGLU MLP。

对芯片设计影响最大的几项：

- decode 每 token 需读取 1.94 GB 权重（MXFP8）。310 GB/s 带宽下理论上限约 160 token/s，按 60–70% 效率折算是 96–112 token/s。decode 受带宽限制，prefill 受算力限制，两者对硬件的要求不同。
- 18 层 DeltaNet 的循环状态为 FP32、16×128×128/层，共 18.9 MB，不随上下文变化，必须常驻片上 SRAM。6 层全注意力的 KV cache 则随上下文线性增长，256K 时为 3.22 GB。
- 矩阵引擎算力下限约 302 GMAC/s（M=1 的 MXFP8 GEMV）。若规划投机解码，验证 pass 一次处理 γ+1 个 token，规格要按 1 TMAC/s 量级考虑。
- lm_head 是最大的单个算子，占 decode 算力的 27%、带宽的 27%，也是量化风险最集中的位置。
- MTP 头可用于投机解码，收益取决于草稿词表：γ=2、共享 248K 词表约 1.37×；剪枝到 8K 词表、γ=3 约 2.25×。
- 一张 448×448 图像的视觉塔前向约 273.1 GMAC。连同 196 个视觉 token 走文本主干，图像相关计算约占一段 1024-token prompt 的 27.7%。

后面按"模型事实、层结构、逐算子、算力与带宽、硬件映射、量化、结论"展开。

---

## 目录

- 摘要

**第一部分　事实与全景**
- 第 1 章　事实基线
- 第 2 章　模型全景

**第二部分　结构：每一层用了什么**
- 第 3 章　层清单（24 层逐层）
- 第 4 章　四种层型的子模块清单

**第三部分　计算：每个算子做什么**
- 第 5 章　通用算子库
- 第 6 章　四种层型的逐算子拆解（40 张算子卡）
- 第 7 章　视觉塔与输出头（视觉塔 10 张卡 + 输出头 6 张）

**第四部分　核算与映射**
- 第 8 章　三本账：算力 / 带宽 / 缓存
- 第 9 章　时间线：一个 token 走完全程
- 第 10 章　算子 → 硬件映射
- 第 11 章　量化归属

**第五部分　结论**
- 第 12 章　对芯片设计的结论与待验证清单

**附录**
- A　符号表
- B　复现脚本

---
---

# 第一部分　事实与全景

---

# 第 1 章　事实基线

这一章定下后续所有核算的事实来源：模型是什么类型、有多少参数、超参取什么值。凡是后面出现的数字，都能在这里找到出处。

## 1.1 数据来源与核对方法

| 来源 | 取得内容 | 可信度 |
|---|---|---|
| `config.json`（HF 主站 / hf-mirror / ModelScope 三处一致） | 全部超参 | 确定 |
| `model.safetensors.index.json` | 632 个张量的名字与分片，总字节 4,548,144,832 | 确定 |
| `transformers` 的 `modeling_qwen3_5.py` | 每个前向的实际算子与顺序 | 确定 |
| 权重文件本体 | 未下载（4.5 GB），形状按 config 推导 | 推导 |

总参数的交叉验证：`4,548,144,832 B ÷ 2`（BF16）= 2,274.07 M，与逐项累加的结果相差 2,592 个参数（0.0001%）。两者一致，后续核算以逐项累加值为准。

## 1.2 关键超参

### 文本主干

| 参数 | 值 | 备注 |
|---|---|---|
| `hidden_size` | **2048** | 残差流宽度 |
| `num_hidden_layers` | **24** | |
| `layer_types` | 3×`linear_attention` : 1×`full_attention`，共 6 组 | 全注意力落在第 3/7/11/15/19/23 层 |
| `num_attention_heads` | **8** | 全注意力层 |
| `head_dim` | **256** | 8×256 = 2048 = hidden |
| `num_key_value_heads` | **2** | GQA 4:1，KV 维 = 512 |
| `attn_output_gate` | **true** | q_proj 双宽，attention 输出过 sigmoid 门 |
| `linear_num_key_heads` | 16 | DeltaNet |
| `linear_key_head_dim` | 128 | 16×128 = 2048 |
| `linear_num_value_heads` | 16 | |
| `linear_value_head_dim` | 128 | 16×128 = 2048 |
| `linear_conv_kernel_dim` | 4 | 短卷积核长 |
| `intermediate_size` | **6144** | MLP |
| `vocab_size` | **248320** | |
| `tie_word_embeddings` | **true** | 与 lm_head 共享 |
| `rms_norm_eps` | 1e-6 | |
| `mamba_ssm_dtype` | **float32** | DeltaNet 状态精度，写死在 config |
| `rope_theta` | 1e7 | |
| `partial_rotary_factor` | **0.25** | 256 维中仅 64 维旋转 |
| `rope_type` | mRoPE，`mrope_section` [11,11,10]，interleaved | 纯文本时退化为普通 RoPE |
| `max_position_embeddings` | **262144** | 256K |
| `mtp_num_hidden_layers` | **1** | 多 token 预测头 |

### 视觉塔

| 参数 | 值 |
|---|---|
| `depth` | 24 blocks |
| `hidden_size` | 1024 |
| `intermediate_size` | 4096 |
| `num_heads` | 16（head_dim 64） |
| `patch_size` / `temporal_patch_size` | 16 / 2 |
| `spatial_merge_size` | 2 |
| `num_position_embeddings` | 2304 |
| `out_hidden_size` | 2048（对齐文本宽度） |
| `hidden_act` | gelu_pytorch_tanh |
| `deepstack_visual_indexes` | 空 → 无 DeepStack |

### 特殊 token

`eos = 248044`，`image_token = 248056`，`video_token = 248057`，`vision_start = 248053`，`vision_end = 248054`。

## 1.3 参数量核算

```
┌─ 文本主干 ────────────────────────────────────── 1,881.8 M
│   embed_tokens (248320×2048)                        508.56 M   ← 与 lm_head 共享
│   input/output 共用：
│     layer 0,1,2,4,5,6,8,9,10,12,13,14,16,17,18,20,21,22（18 层 DeltaNet）
│       每层 58.81 M  →  ×18 =                      1,058.66 M
│     layer 3,7,11,15,19,23（6 层全注意力）
│       每层 52.43 M  →  ×6  =                        314.60 M
│   final norm                                          0.002 M
│
├─ 视觉塔 ────────────────────────────────────────   331.42 M
│   patch_embed (Conv3d 3→1024, k=2×16×16)              1.57 M
│   pos_embed (2304×1024)                               2.36 M
│   blocks ×24（每块 12.60 M）                        302.31 M
│   merger（1024→4096→2048）                           25.17 M
│
└─ MTP 头 ───────────────────────────────────────   60.83 M
    mtp.layers.0（= 1 个完整全注意力层）                52.43 M
    mtp.fc（4096→2048）+ 3 个 norm                      8.40 M
─────────────────────────────────────────────────────────
    合计                                           2,274.07 M  （权重文件实测 2,274.07 M）
```

文本主干的 1.88B 可以分成三块：18 层 DeltaNet 占 56.3%（1.059B），6 层全注意力占 16.7%（0.315B），共享的 embedding/lm_head 占 27.0%（0.509B）。

---
---

# 第 2 章　模型全景

模型由三部分组成：视觉塔、文本主干、MTP 头。文本主干 24 层用两种 token mixer 交替，其余结构完全一致。全模型的计算只有四种形态，后面的硬件映射直接按这四类划分。

## 2.1 三个子模型

```
                    ┌─────────────────────────┐
   图像/视频  ────► │  视觉塔（24 blocks）      │ ──► 1024→2048 投影
                    │  非因果注意力 + GELU-MLP  │      成 N 个视觉 token
                    └─────────────────────────┘           │
                                                           ▼
   token_id  ────► [embed_tokens 查表] ────────────────► 拼接进序列
                                                           │
                    ┌──────────────────────────────────────┴───┐
                    │  文本主干（24 层，3:1 交替）               │
                    │  DeltaNet ×18 ／ Attention ×6            │
                    │  每层都挂 SwiGLU MLP                     │
                    └──────────────────────────────────────┬───┘
                                                           ▼
                                                  [final RMSNorm]
                                                           │
                                     ┌─────────────────────┴──────┐
                                     ▼                            ▼
                          [lm_head（=embedᵀ）]            [MTP 头（投机解码）]
                                     │                            │
                                     ▼                            ▼
                               logits → 采样                 草稿 token
```

视觉塔只在输入含图像时运行，一张图跑一次前向，不参与逐 token 生成。文本主干每个 token 都要过 24 层，是 decode 的主要开销。MTP 头在 `transformers` 里被 `_keys_to_ignore_on_load_unexpected = [r"^mtp.*"]` 排除，标准推理不加载，只在投机解码时使用；它自带一层全注意力。

## 2.2 文本主干的层型配比

`layer_types` 实测序列（共 24 项）：

```
索引:  0  1  2  3 | 4  5  6  7 | 8  9 10 11 |12 13 14 15 |16 17 18 19 |20 21 22 23
类型:  L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A
                   ↑              ↑              ↑              ↑              ↑
              L = linear_attention (Gated DeltaNet)   每 4 层放 1 层全注意力，全注意力在组尾
              A = full_attention  (Gated Attention)   最后一层（23）是全注意力
```

这样配的原因在于两种 mixer 的分工：全注意力保留完整的 KV cache，负责精确检索；DeltaNet 用固定大小的状态做长程压缩，成本恒定。3:1 让 KV cache 只落在 1/4 的层上，在 256K 上下文下仍然保得住检索能力。

## 2.3 计算形态的四个类别

全模型的算子都能归入下表四类，后面的硬件映射按这四类划分。

| 形态 | 定义 | 典型算子 | 特征 |
|---|---|---|---|
| **GEMM / GEMV** | 向量或矩阵 × 权重矩阵 | 所有投影、lm_head、QKᵀ、AV | 唯一能上矩阵引擎的形态 |
| **归约** | 沿某一维求和/求最值 | RMSNorm、L2 norm、softmax 分母、amax | 需要跨 lane 通信 |
| **逐元素** | 同形状逐点运算 | silu、sigmoid、exp、门控乘、残差加、RoPE | 天然 SIMD |
| **串行递推** | 第 n 步依赖第 n-1 步 | DeltaNet 状态更新、三角求逆 | **不能向量化**，是延迟关键路径 |

外加两个非计算类别：**查表**（embedding）、**搬运**（KV cache、量化 pass）。

---
---

# 第二部分　结构：每一层用了什么

---

# 第 3 章　层清单

24 层其实只有两种结构，每层再挂同一个 MLP，所以只需要理解两个层型。同类型层之间完全同构：张量名、形状、算子顺序都一样，差别只在权重数值。真正影响硬件的是相邻层之间怎么传状态——DeltaNet 传固定大小的循环状态，Attention 传随长度增长的 KV cache。

## 3.1 24 层总表

| 层号 | 类型 | Token Mixer | MLP | 每层参数 | Mixer 独有算子 |
|---:|---|---|---|---:|---|
| 0 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 短卷积、门控递推 |
| 1 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同上 |
| 2 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同上 |
| **3** | **Attention** | `self_attn` | ✓ | 52.43 M | RoPE、softmax、KV cache |
| 4 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 5 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 6 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| **7** | **Attention** | `self_attn` | ✓ | 52.43 M | 同 3 |
| 8 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 9 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 10 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| **11** | **Attention** | `self_attn` | ✓ | 52.43 M | 同 3 |
| 12 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 13 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 14 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| **15** | **Attention** | `self_attn` | ✓ | 52.43 M | 同 3 |
| 16 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 17 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 18 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| **19** | **Attention** | `self_attn` | ✓ | 52.43 M | 同 3 |
| 20 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 21 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| 22 | DeltaNet | `linear_attn` | ✓ | 58.81 M | 同 0 |
| **23** | **Attention** | `self_attn` | ✓ | 52.43 M | 同 3 |

**合并后**：

| 层型 | 数量 | 每层参数 | 小计 | 结构 |
|---|---:|---:|---:|---|
| A　Gated DeltaNet | 18 | 58.81 M | 1,058.7 M | 18 层完全同构 |
| B　Gated Attention | 6 | 52.43 M | 314.6 M | 6 层完全同构 |
| C　SwiGLU MLP | 24 | 37.75 M | 906.0 M | 24 份完全同构，A、B 各挂一份 |
| D　MTP | 1 | 60.83 M | 60.83 M | 独立 |

MLP 不是独立层，而是 A、B 内部的共用子模块。上表单列它的参数，是为了说明 MLP 单独就占 40%。

## 3.2 层的通用骨架

两种层共用同一个模板，只有中间的 `mixer` 不同。归一化只读不写，把归一化结果送进 mixer；残差加是唯一把结果写回总线的位置。

```
x [1,2048]
 │
 ├─ 读出 ─► r = input_layernorm(x)  [1,2048]
 │            │
 │            ▼
 │          y = mixer(r)  [1,2048]        ← 层型 A（3.3）/ B（3.4）
 │            │
 └─ x = x + y ◄┘                          ← 残差 ①，唯一写回 x 的点
 │
 ├─ 读出 ─► r = post_attention_layernorm(x)  [1,2048]
 │            │
 │            ▼
 │          z = mlp(r)  [1,2048]          ← 子模块 C（3.5）
 │            │
 └─ x = x + z ◄┘                          ← 残差 ②
 │
输出 x [1,2048]（形状与输入完全相同）
```

**读图约定（第 3 章所有 DAG 通用）**

| 记号 | 含义 |
|---|---|
| `─►` / `──►` / `───►` | 同 token 内的数据依赖，箭头上带计算 |
| `┄` / `┄┄►` | 纯视图（`split` / `reshape` / `transpose`），0 MAC，只改索引与 stride |
| `═` / `══►` | 跨 token 状态读写（`conv_state` / `recurrent_state` / KV cache） |
| `[G0]` `[G1]` … | 并行组：同组内互不依赖，可并发发射；组号小的必须先完成 |
| 横向分支 | 只表示并行关系，不表示时间先后；汇合点之前的所有分支都必须就绪 |

**层内只有三种形状变换**（其余都是逐元素）：

| 变换 | 形状关系 | 例子 | 通道语义 |
|---|---|---|---|
| 稠密线性（GEMM/GEMV） | `[K] → [N]` | `in_proj_qkv` 2048→6144、`gate_proj` 2048→6144 | 全部通道混合，每个输出 = 全 K 维内积 |
| 逐元素 / 逐通道 | `[N] → [N]` | RMSNorm 缩放、SiLU、门控乘、残差加 | 一进一出，通道对齐 |
| 纯视图 | `[N] → [M₁]…[Mₖ]`（∏Mᵢ = N） | `split` 6144→3×2048、`reshape` 2048→16×128 | 不改数，只改索引 |

所以维度变化只有两种来源：中间隔了一次 GEMM（宽度等于学出来的读出方向个数），或者只是换了个视图（总元素数不变）。3.3–3.6 的每张图都按这条规则标注；`[G]` 编号在同一层内连续，跨层重新从 `G0` 开始。

残差流的宽度在整个 24 层中恒为 `[2048]`，每层只是在这个向量上叠加两个修正量。

## 3.3 层型 A：Gated DeltaNet（18 层）

DeltaNet 替代 attention 的方式，是把所有历史 token 压缩进一个固定的 `128×128` 状态矩阵，每来一个新 token 更新一次。

子模块共 9 个，全部挂在 `linear_attn.*` 下：

| 张量 | 形状 | 作用 |
|---|---|---|
| `in_proj_qkv` | [6144, 2048] | 一次投影出 q、k、v（各 2048 维，拼在一起） |
| `in_proj_z` | [2048, 2048] | 输出门控 z（进 RMSNormGated 当 gate） |
| `in_proj_b` | [16, 2048] | 每头一个 β（写入门控） |
| `in_proj_a` | [16, 2048] | 每头一个 a（衰减率） |
| `conv1d` | [6144, 1, 4] | 深度可分离因果卷积，作用在拼好的 qkv 上，**卷积后接 SiLU** |
| `A_log` | [16] | 衰减率的可学习基（Mamba 式离散化） |
| `dt_bias` | [16] | 同上 |
| `norm` | [128] | `RMSNormGated`，按 head 的 value 维归一后乘 `silu(z)` |
| `out_proj` | [2048, 2048] | 输出投影 |
| `input_layernorm` / `post_attention_layernorm` | [2048] | 骨架自带 |

**独有的三个算子**（其它层型没有）：

1. **融合 QKV 投影**：与 attention 不同，q/k/v 是一个矩阵一次算出来的，DMA 侧要按 2048/2048/2048 切分。
2. **短因果卷积 + SiLU**：跨 token 的局部感受野，需要保存前 3 个 token 的 qkv（滚动缓存）。
3. **门控 delta 递推**：`S ← S·diag(exp(g)) + β·kᵀ(v − S·k)`，每 token 更新一次全状态。

### 3.3.1 计算流（decode 单 token，依赖 DAG）

```
x [1,2048] ──► [G0] input_layernorm ──► r [1,2048]
                                          │
      ┌──────────────┬────────────────────┼────────────────────┐
      ▼              ▼                    ▼                    ▼
 [G1] in_proj_qkv  [G1] in_proj_a    [G1] in_proj_b       [G1] in_proj_z ───┐
      [1,6144]      [1,16]             [1,16]               [1,2048]        │
      │              │                    │                                 │
      ▼              ▼                    ▼                                 │
 [G2] conv1d+SiLU  g = −exp(A_log)·    β = σ(b)                             │
      [1,6144]      softplus(a+dt)      [1,16]                              │
      │ ═conv_state  [1,16]                │                                 │
      ▼                │                  │                                 │
 [G3] split ┄          │                  │                                 │
  q,k,v [1,16,128]     │                  │                                 │
      │                │                  │                                 │
      ▼                │                  │                                 │
 [G4] L2norm(q),       │                  │                                 │
      L2norm(k)        │                  │                                 │
      │                │                  │                                 │
      └────────────────┴──────────────────┘                                 │
                       ▼                                                    │
 [G5] gated delta rule ═ recurrent_state [16,128,128]                       │
      q̂,k̂,v,β,g ──► o_core [1,16,128]                                      │
                       │                                                    │
                       ▼                                                    │
 [G6] RMSNormGated(o_core, z) ◄──────────────────────────────────────────────┘
                       │ [1,2048]
                       ▼
 [G7] out_proj ──► y [1,2048]
                       ▼
 [G8] x = x + y ──► x [1,2048]
                       ▼
 [G9] post_attention_layernorm ──► r2 [1,2048]
                       │
        ┌──────────────┴──────────────┐
        ▼                             ▼
 [G10] gate_proj [1,6144]      [G10] up_proj [1,6144]
        │                             │
        └──────────────┬──────────────┘
                       ▼
 [G11] silu(g) ⊙ u ──► h [1,6144]
                       ▼
 [G12] down_proj ──► z2 [1,2048]
                       ▼
 [G13] x = x + z2 ──► x [1,2048]
```

### 3.3.2 逐算子映射表

| 组 | 算子 | 调用（源码） | 形状（in → out） | 数值变换 | 依赖 |
|---|---|---|---|---|---|
| G0 | input_layernorm | `self.input_layernorm` → `Qwen3_5RMSNorm.forward`（hub kernel `RMSNormZeroCentered`） | `[1,2048] → [1,2048]` | `y_i = x_i·rsqrt(mean(x²)+1e-6)·(1+w_i)` | x |
| G1 | in_proj_qkv | `nn.Linear(2048,6144)`（`:530`） | `[1,2048] → [1,6144]` | `qkv_o = Σ_i W[o,i]·r_i` | G0 |
| G1 | in_proj_z | `nn.Linear(2048,2048)`（`:531`） | `[1,2048] → [1,2048]` | `z_o = Σ_i W_z[o,i]·r_i` | G0 |
| G1 | in_proj_a | `nn.Linear(2048,16)`（`:532`） | `[1,2048] → [1,16]` | `a_h = Σ_i W_a[h,i]·r_i` | G0 |
| G1 | in_proj_b | `nn.Linear(2048,16)`（`:533`） | `[1,2048] → [1,16]` | `b_h = Σ_i W_b[h,i]·r_i` | G0 |
| G2 | 因果卷积 + SiLU | `causal_conv1d_update(...)`（`:565`） | `[1,6144] + state[6144,3] → [1,6144]` | `y_c = silu(Σ_{j=0..3} W[c,j]·x_{t+j−3})` | G1(qkv)，`═conv_state` |
| G2 | β | `b.sigmoid()` | `[1,16] → [1,16]` | `β_h = 1/(1+e^{−b_h})` | G1(b) |
| G2 | g | `−exp(A_log)·softplus(a+dt_bias)` | `[1,16] → [1,16]` | `g_h = −e^{A_log,h}·ln(1+e^{a_h+dt_h})` | G1(a) |
| G3 | split + reshape | `torch.split(qkv,[2048,2048,2048])`（`:591`）+ `reshape` | `[1,6144] → 3×[1,16,128]` | **纯索引**：`q[h,:]=qkv[128h:128h+128]`，k/v 段各偏移 2048 | G2 |
| G4 | L2 norm(q), L2 norm(k) | kernel 内 `l2norm(x, dim=-1, eps=1e-6)`（`:280–283`） | `[1,16,128] → [1,16,128]` | `x̂ = x/√(Σx²+1e-6)`，再乘 `d^{−1/2}` | G3 |
| G5 | gated delta rule | `torch_recurrent_gated_delta_rule(...)`（`:621`） | `q̂,k̂,v,β,g → [1,16,128]` | `S ← S·diag(e^g) + β·kᵀ(v − S·k)`；`o = S·q̂` | G4, G3(v), G2(β,g)，`═recurrent_state` |
| G6 | RMSNormGated | `self.norm(o_core, z)`（`:646`） | `[1,16,128] × [1,16,128] → [1,2048]` | 每头 128 维 `RMSNorm(x)·w ⊙ silu(z)` | G5, G1(z) |
| G7 | out_proj | `nn.Linear(2048,2048)`（`:526`） | `[1,2048] → [1,2048]` | `y_o = Σ_i W_out[o,i]·c_i`，**跨头混合** | G6 |
| G8 | 残差加 ① | Python `+`（`:893`） | `[1,2048] → [1,2048]` | `x = x + y` | G7 |
| G9 | post_attention_layernorm | `self.post_attention_layernorm`（`:896`） | `[1,2048] → [1,2048]` | 同 G0 | G8 |
| G10 | gate_proj / up_proj | `self.gate_proj` / `self.up_proj`（`Qwen3_5MLP.forward`） | `[1,2048] → [1,6144]` ×2 | `g_n=ΣW_g[n,i]r_i`、`u_n=ΣW_u[n,i]r_i` | G9 |
| G11 | SwiGLU | `F.silu(g) * u` | `[1,6144] × [1,6144] → [1,6144]` | `a_n = silu(g_n)·u_n` | G10 |
| G12 | down_proj | `self.down_proj`（`nn.Linear(6144,2048)`） | `[1,6144] → [1,2048]` | `y_m = Σ_n W_d[m,n]·a_n` | G11 |
| G13 | 残差加 ② | Python `+`（`:899`） | `[1,2048] → [1,2048]` | `x = x + z2` | G12 |

### 3.3.3 关键路径与并行机会

- 关键路径决定层内最短延迟：`G0 → G1(qkv) → G2(conv) → G3(split) → G4(L2) → G5(递推) → G6 → G7 → G8 → G9 → G10 → G11 → G12 → G13`。
- `z` 分支不在关键路径上，但生命周期最长：G1 产出、G6 才消费，中间跨过整个 conv 和递推。这部分每 token 4 KB 必须常驻，调度器最容易在这里溢出（A⑪ 坑 2）。
- `a`/`b` 两支各只有 32 K MAC，却压在关键路径上，因为 `g`/`β` 是 G5 的输入。它们必须和 `in_proj_qkv` 同批发射；如果串行排在 12.58 M 的那次 GEMM 之后，等于给整层多加一次 GEMV 的启动延迟（A③ 坑 1）。
- 可以藏起来的并行窗口：conv 和递推只依赖 qkv 支路，所以 `in_proj_z`、`in_proj_a`、`in_proj_b` 这三笔 GEMV 只要支持异步，就能完全落进 G2–G5 的阴影里。
- G4 与 G2 的 β/g 互不依赖，可以同时发射，但 G4 要等 G3 的 split。
- 层内有两个跨 token 状态点：G2 的 `conv_state`（最近 3 帧）和 G5 的 `recurrent_state`（262,144 元素，FP32 读写各 1 MiB）。后者是层内延迟的主项，也是唯一不能靠并行隐藏的强依赖。
- G3 的 `split`/`reshape` 是 0 MAC，但 conv 需要 `[B,6144,S]` 的 channels-first 布局，`transpose` 在 `F.conv1d` 里可能物化一次（A④ 坑 6）。硬件上建议在 G1 输出处直接按 128 粒度分块写，绕开这两个视图。
- prefill 时 G2 从单 token 卷积变成 `F.conv1d(padding=3)` 的全长卷积，G5 从逐 token 递推变成 `torch_chunk_gated_delta_rule` 的分块矩阵算法（A⑩），图上的串行链会被展成沿 S 并行的矩阵乘，关键路径随之改变。

## 3.4 层型 B：Gated Attention（6 层）

Mixer 子模块 6 个，加上骨架自带 2 个：

| 张量 | 形状 | 作用 |
|---|---|---|
| `q_proj` | [4096, 2048] | 双宽：前半是 query（8×256），后半是输出门控 |
| `k_proj` | [512, 2048] | 2 个 KV 头 × 256 |
| `v_proj` | [512, 2048] | 同上 |
| `o_proj` | [2048, 2048] | 输出投影（在门控之后） |
| `q_norm` | [256] | 按 head_dim 归一，不是对整个 2048 |
| `k_norm` | [256] | 同上 |
| `input_layernorm` / `post_attention_layernorm` | [2048] | 骨架自带 |

和 DeltaNet 相比多出三个算子：

1. 部分 RoPE：只转 256 维中的前 64 维（`partial_rotary_factor=0.25`），其余 192 维直接透传。
2. 因果 softmax：沿 S 维归约，FP32。
3. KV cache：每层每 token 写 2×256×2 = 1 KB（FP16）。

### 3.4.1 计算流（decode 单 token，依赖 DAG）

```
x [1,2048] ──► [G0] input_layernorm ──► r [1,2048]
                                          │
      ┌──────────────┬────────────────────┴────────────┐
      ▼              ▼                                 ▼
 [G1] q_proj     [G1] k_proj                      [G1] v_proj
      [1,4096]      [1,512]                          [1,512]
      │              │                                 │
      │ view(8,512)+chunk → q [1,8,256]                  ▼
      │              │                          [G2] 写 V cache
      ▼              ▼                          v_cache [1,2,S,256]
 [G2] q_norm    [G2] k_norm [1,2,256]            （供 G6）
      ▼              ▼
 [G3] RoPE      [G3] RoPE(64/256)
      │              ▼
      │         [G3] 写 K cache
      │         k_cache [1,2,S,256]（供 G4）
      │              │
      ▼              ▼
 [G4] Q·Kᵀ ◄════════┘
      │  s [1,8,1,S]
      ▼
 [G5] +因果 mask, softmax(FP32)
      │  p [1,8,1,S]
      ▼
 [G6] A·V ◄═ v_cache
      │  o [1,8,1,256] → [1,1,2048]
      ▼
 [G7] o ⊙ σ(gate) ◄── gate [1,2048]（G1 产出，全层驻留）
      │  [1,2048]
      ▼
 [G8] o_proj ──► y [1,2048]
      ▼
 [G9] x = x + y ──► x [1,2048]
      ▼
 [G10] post_attention_layernorm ──► r2 [1,2048] ──► MLP（3.5）
```

> `q_proj` 的 4096 维在 G1 内就地拆成两支：`q [1,8,256]` 进 G2；`gate [1,2048]` 是旁路，一直驻留到 G7 才参与门控（图中不再画线）。

### 3.4.2 逐算子映射表

| 组 | 算子 | 调用（源码） | 形状（in → out） | 数值变换 | 依赖 |
|---|---|---|---|---|---|
| G0 | input_layernorm | `self.input_layernorm`（L872）→ `Qwen3_5RMSNorm.forward` | `[1,2048] → [1,2048]` | `x·rsqrt(mean(x²)+1e-6)·(1+w)` | x |
| G1 | q_proj | `self.q_proj`（L774，`nn.Linear(2048, 4096)`） | `[1,2048] → [1,4096]` | `y = W_q·r`；`view(…,8,512)` 后 `chunk(2)` → q `[8,256]`、gate `[8,256]` | G0 |
| G1 | k_proj / v_proj | `self.k_proj` / `self.v_proj`（L780–781，`nn.Linear(2048, 512)`） | `[1,2048] → [1,512]` | `k = W_k·r`、`v = W_v·r`；`view(…,2,256)` | G0 |
| G2 | q_norm / k_norm | `self.q_norm` / `self.k_norm`（L779–781，`Qwen3_5RMSNorm(256)`） | q `[1,8,256]`、k `[1,2,256]`，均不变 | 每头 256 维 `x·rsqrt(mean₂₅₆(x²)+1e-6)·(1+w)` | G1 |
| G3 | 部分 RoPE | `apply_rotary_pos_emb(q,k,cos,sin)`（L661） | 形状不变（q `[1,8,1,256]`、k `[1,2,S,256]`） | 前 64 维 half-split 旋转，后 192 维透传 | G2 |
| G3 | K cache 追加 | `past_key_values.update(k,v,…)`（L786–787） | k → `[1,2,S+1,256]` | 纯搬运（0 MAC），K 已 norm、已旋转 | G3 |
| G2 | V cache 追加 | 同一 `past_key_values.update`（硬件可在 G1 后单独写 V） | v → `[1,2,S+1,256]` | 纯搬运（0 MAC），V **未** norm、**未**旋转 | G1 |
| G4 | Q·Kᵀ | `torch.matmul(q, kᵀ)` + 前置 `repeat_kv(k,4)`（L699） | q `[1,8,1,256]` × k `[1,8,S,256]` → s `[1,8,1,S]` | `s = α·Σ_{d=0..255} q_d·k_d`，α = 1/16 | G3 |
| G5 | 因果 mask + softmax | `attn_weights + mask` → `F.softmax(..., dtype=float32)`（L725–728） | s `[1,8,1,S]` → p `[1,8,1,S]` | `p = exp(s−m)/Σexp(s−m)`，FP32 | G4 |
| G6 | A·V | `torch.matmul(p, v)`（L730）→ `transpose(1,2).contiguous()` | p `[1,8,1,S]` × v `[1,8,S,256]` → `[1,8,1,256]` → `[1,1,2048]` | `o = Σ_{t=1..S} p_t·v_t`；末步 reshape 是布局变换 | G5, G2(v) |
| G7 | 输出门控 | `attn_output * torch.sigmoid(gate)`（L804） | `[1,2048] × [1,2048] → [1,2048]` | `o_i ← o_i·σ(g_i)`，**在 o_proj 之前** | G6, G1(gate) |
| G8 | o_proj | `self.o_proj`（L807，`nn.Linear(2048,2048)`） | `[1,2048] → [1,2048]` | `y_o = Σ_i W_o[o,i]·c_i`，**跨头混合** | G7 |
| G9 | 残差加 ① | `residual + hidden_states`（L893） | `[1,2048] → [1,2048]` | `x = x + y` | G8 |
| G10 | post_attention_layernorm | `self.post_attention_layernorm`（L896） | `[1,2048] → [1,2048]` | 同 G0 | G9 |

### 3.4.3 关键路径与并行机会

- 关键路径：`G0 → q_proj(G1) → q_norm(G2) → RoPE(G3) → Q·Kᵀ(G4) → softmax(G5) → A·V(G6) → 门控(G7) → o_proj(G8) → 残差(G9)`。
- 有三条并行支路：Q 支路（norm→RoPE）、K 支路（norm→RoPE→写 cache）、V 支路（直接写 cache）。K/V 支路不在关键路径上，它们的代价体现在 G4/G6 的 K 维读取上，而不在投影本身。
- gate 生命周期最长：G1 产出、G7 消费，中间跨 3 次矩阵乘和 softmax，每 token 4 KB 必须全层驻留（B② 坑 3）。
- 有几处顺序是定义性的，不能重排：`q_norm` 在 `transpose` 之前（B④），RoPE 在 KV 写入之前（B⑤/B⑥），门控在 `o_proj` 之前（B⑩）。任何一处顺序错都不是精度问题，而是语义错误。
- `repeat_kv` 是 stride-0 的逻辑视图，8 个 Q 头共享 2 个 KV 头。如果硬件按 8 头各读一份 KV，G4/G6 的 KV 带宽会直接变成 4 倍（B⑦ 坑 1）。
- G5 是层内唯一逐行全 S 归约加超越函数的步骤，6 层合计约 19.7 万次 exp。它比同层所有 GEMM 都慢，是 attention 的延迟核心（B⑧ 坑 4）。
- decode 时 G4/G6 是 M=8、K=S 的 GEMV，算力利用率不到 10%，受 KV 带宽支配；prefill 时它们变成 `S×S×256` 的大 GEMM，G5 的 mask 也变成 `S×S`，整层计算形态完全不同（B⑦/B⑨）。
- 残差相加前最后一次干净舍入：G8 的 `o_proj` 输出建议以 FP32 直接进 G9，不要先截成 FP16 再相加（B⑪/B⑫）。

## 3.5 子模块 C：SwiGLU MLP（24 层各挂一份）

| 张量 | 形状 | 作用 |
|---|---|---|
| `mlp.gate_proj` | [6144, 2048] | 门控分支 |
| `mlp.up_proj` | [6144, 2048] | 值分支 |
| `mlp.down_proj` | [2048, 6144] | 回投到 2048 |

计算：`y = down( silu(gate(x)) ⊙ up(x) )`

24 层完全同构，是模型里最规整、最适合先优化也最适合先量化的部分。

计算流如下，M2a 与 M2b 是唯一可并行的两支：
```
r [1,2048] ──► [M1] post_attention_layernorm ──► r' [1,2048]
                                                    │
                 ┌──────────────────────────────────┴───────────────┐
                 ▼                                                  ▼
        [M2a] gate_proj [1,6144]                          [M2b] up_proj [1,6144]
                 │                                                  │
                 ▼                                                  │
        [M3a] silu(g) [1,6144]                                      │
                 │                                                  │
                 └──────────────────────┬───────────────────────────┘
                                        ▼
                            [M3b] a = silu(g) ⊙ u  [1,6144]
                                        ▼
                            [M4] down_proj  [1,2048]
                                        ▼
                            [M5] x = x + y  [1,2048]
```

| 组 | 算子 | 调用 | 形状（in → out） | 数值变换 | 依赖 |
|---|---|---|---|---|---|
| M1 | post_attention_layernorm | `Qwen3_5RMSNorm.forward` | `[1,2048] → [1,2048]` | `x·rsqrt(mean₂₀₄₈(x²)+1e-6)·(1+w)` | 残差 ① 后的 x |
| M2a | gate_proj | `self.gate_proj`（`nn.Linear(2048,6144)`） | `[1,2048] → [1,6144]` | `g_n = Σ_k W_gate[n,k]·r'_k` | M1 |
| M2b | up_proj | `self.up_proj`（`nn.Linear(2048,6144)`） | `[1,2048] → [1,6144]` | `u_n = Σ_k W_up[n,k]·r'_k` | M1 |
| M3a | SiLU | `F.silu(g)` | `[1,6144] → [1,6144]` | `g·σ(g)` | M2a |
| M3b | 门控乘 | `*` | `[1,6144] × [1,6144] → [1,6144]` | `a_n = silu(g_n)·u_n` | M3a, M2b |
| M4 | down_proj | `self.down_proj`（`nn.Linear(6144,2048)`） | `[1,6144] → [1,2048]` | `y_m = Σ_n W_down[m,n]·a_n` | M3b |
| M5 | 残差加 ② | Python `+`（`:899`） | `[1,2048] → [1,2048]` | `x = x + y`，FP32 | M4 |

**并行与顺序要点**

- M2a 和 M2b 读同一个 `r'`，互不依赖，是 MLP 内唯一的并行机会。也可以离线把两份权重拼成 `W_cat = [W_gate; W_up] ∈ R^{12288×2048}`，一次 GEMM 出 `[g‖u]`，MAC 不变但少一次发射（6.3 M2 坑 1）。
- 汇合点在 M3b：`g` 与 `u` 必须来自同一次归一化，否则两分支差一个舍入，门控会偏（6.3 M3 坑）。
- M1 只做一次归一化、只读一次 4 KB；如果 gate/up 各自重新归一，会多算一遍 11 级归约（6.3 M1 坑）。
- 关键路径是 `M1 → M2a → M3a → M3b → M4 → M5`。`M2b → M3b` 是支路，两条 GEMM 等长，所以并行换来的是吞吐而不是延迟。
- MLP 内没有任何视图操作，`2048 ↔ 6144` 的两次维度变化全部来自 GEMM。

## 3.6 层型 D：MTP 头（1 层，仅投机解码）

| 张量 | 形状 | 作用 |
|---|---|---|
| `mtp.pre_fc_norm_hidden` | [2048] | 归一化主干隐状态 |
| `mtp.pre_fc_norm_embedding` | [2048] | 归一化下一 token 的 embedding |
| `mtp.fc` | [2048, 4096] | 两路拼接后降回 2048 |
| `mtp.layers.0.*`（11 个） | — | 一个完整的全注意力层（同层型 B 的 mixer + MLP） |
| `mtp.norm` | [2048] | 输出归一化 |

`transformers` 默认不加载 MTP（`^mtp.*` 被忽略），但它在端侧很有用：这是一个现成的投机解码草稿头，能把 batch=1 decode 的权重复用次数从 1× 提到 1.37×（γ=2、共享词表）或 2.25×（γ=3、8K 剪枝草稿头），直接绕开内存墙。代价是要配套草稿采样逻辑，不是免费的。

计算流如下，D1 与 D2 可以并行：

```
h_t [1,2048] ────► [D1] pre_fc_norm_hidden ──► h' [1,2048] ──┐
                                                              ├─ cat [1,4096]
token_{t+1} ──► 查表 embed_tokens ──► [1,2048]                │      │
                                                              │      ▼
                 [D2] pre_fc_norm_embedding ──► e' [1,2048] ──┘  [D3] mtp.fc
                                                                     │ [1,2048]
                                                                     ▼
                                            [D4] layers.0（与层型 B 同构：mixer + MLP）
                                                                     │ [1,2048]
                                                                     ▼
                                            [D5] mtp.norm ──► 共享 lm_head ──► logits [248320]
```

| 组 | 算子 | 调用 | 形状（in → out） | 数值变换 | 依赖 |
|---|---|---|---|---|---|
| D1 | pre_fc_norm_hidden | `Qwen3_5RMSNorm` 同构 | `[1,2048] → [1,2048]` | `h·rsqrt(mean(h²)+1e-6)·(1+w)` | 主干末层隐状态 |
| D2 | embedding 查表 + pre_fc_norm_embedding | `embed_tokens(next_token_id)` → norm | `[248320,2048]` 取 1 行 → `[1,2048]` | 查表 0 MAC，再做同样的归一 | 已采样的 `token_{t+1}` |
| D3 | concat + mtp.fc | `mtp.fc`（`nn.Linear(4096,2048)`） | `[h'‖e'] [1,4096] → [1,2048]` | `x = W_fc·[h'‖e']`，h 在前 | D1, D2 |
| D4 | mtp.layers.0 | 完整全注意力层（B①–B⑫ + MLP） | `[1,2048] → [1,2048]` | 见 3.4 / 3.5 | D3 |
| D5 | mtp.norm + 共享 lm_head | `Qwen3_5RMSNorm` → `lm_head`（绑定 `embed_tokens`） | `[1,2048] → [1,248320]` | norm 后 `logits = E·x`（E 即 embedding 矩阵） | D4 |

**并行与顺序要点**

- D1 与 D2 互不依赖，可以并行（两条 VPU lane）。但 D2 的查表依赖上一步采样出的 token，所以跨 token 串行，这是 MTP 的固有延迟下限（6.4 D2 坑 2）。
- 汇合点在 D3 的 concat：顺序必须是 `[h‖e]`，反了不会报错，但输出是噪声（6.4 D3 坑 1）。
- D4 是层型 B 的完整复制（含自己的 KV cache），内部依赖与 3.4 相同。它没有 DeltaNet 递推，这是 MTP 便宜的原因。
- D5 的 `lm_head` 没有独立权重，必须复用绑定的 `embed_tokens`（6.4 D5 坑）。`N=248320` 是全模型最大的一次 GEMV，草稿阶段每 token 都要跑一遍。

## 3.7 层间差异速查

| 问题 | 答案 |
|---|---|
| 18 层 DeltaNet 之间有区别吗 | 没有。张量名、形状、算子顺序完全一致 |
| 6 层 Attention 之间有区别吗 | 没有。同上 |
| 24 个 MLP 之间有区别吗 | 没有。都是 [6144,2048]×2 + [2048,6144] |
| DeltaNet 和 Attention 的本质区别 | 状态传递方式：固定 128×128 状态 vs 增长的 KV cache |
| 位置信息怎么进来 | Attention 层用 RoPE；DeltaNet 层没有 RoPE，靠短卷积和递推隐式携带 |
| 哪些层有跨 token 状态 | 全部 24 层都有：DeltaNet 有 conv_state 和 recurrent_state，Attention 有 KV cache |

## 3.8 单层内部张量流（以 layer 0 为例）

这一节给硬件和测试用，逐行追踪 layer 0 的形状变化（decode，S 为当前上下文）。`[G]` 编号与 3.3.1 对应，`┄` 表示纯视图，`═` 表示跨 token 状态。

```
x                        [1, 2048]          ← 残差流
│
├ [G0] input_layernorm ► r  [1, 2048]                      归约 2048
│
├ [G1] in_proj_qkv ─────► qkv [1, 6144]                    GEMM 2048×6144  ┐
├ [G1] in_proj_z ───────► z   [1, 2048]                    GEMM 2048×2048  │ 四路
├ [G1] in_proj_a ───────► a   [1, 16]                      GEMM 2048×16    │ 并行
├ [G1] in_proj_b ───────► b   [1, 16]                      GEMM 2048×16    ┘
│
├ [G2] conv1d(+SiLU) ───► qkv' [1, 6144]                   深度卷积 4 taps
│     └ ═conv_state [6144, 3]
├ [G2] β = sigmoid(b)  [16]                               逐元素
├ [G2] g = −exp(A_log) · softplus(a + dt_bias)  [16]       FP32
│
├ [G3] split ┄──────────► q,k,v 各 [1, 16, 128]            0 MAC（纯视图）
│
├ [G4] L2 norm(q), L2 norm(k)                             归约 128 ×32
│
├ [G5] gated delta rule ─► o_core [1, 16, 128]             逐头矩阵-向量
│     └ ═recurrent_state [16, 128, 128]  ← 262,144 元素
│
├ [G6] RMSNormGated(o_core, z) ─► [1, 2048]                归约 128 ×16 + silu
├ [G7] out_proj ────────────► y [1, 2048]                  GEMM 2048×2048
│
x = x + y  [G8]             [1, 2048]                      逐元素
│
├ [G9] post_attention_layernorm ► r [1, 2048]              归约 2048
├ [G10] gate_proj ──────► g [1, 6144]                      GEMM 2048×6144  ┐并行
├ [G10] up_proj ────────► u [1, 6144]                      GEMM 2048×6144  ┘
├ [G11] silu(g) ⊙ u ────► h [1, 6144]                      逐元素 + exp
├ [G12] down_proj ──────► z [1, 2048]                      GEMM 6144×2048
│
x = x + z  [G13]            [1, 2048]                      逐元素
```

**并行结构速查（decode）**

| 并行组 | 内容 | 性质 |
|---|---|---|
| G0 | `input_layernorm` | 单点，2048 宽归约 |
| G1 | `in_proj_qkv` / `in_proj_z` / `in_proj_a` / `in_proj_b` | 四路同时读同一个 r，互不依赖 |
| G2 | `conv1d` / `β` / `g` | conv 等 qkv，β 等 b，g 等 a，三者可并发 |
| G3 | `split` | 纯视图，依赖 conv 输出 |
| G4 | `L2 norm(q)`, `L2 norm(k)` | 两条 128 宽归约，可并发 |
| G5 | `gated delta rule` | 汇合点：等 q̂/k̂（G4）、v（G3）、β/g（G2） |
| G6 | `RMSNormGated` | 汇合点：等 o_core（G5）与 z（G1，存活最久） |
| G7–G13 | `out_proj` → 残差 → 后置归一 → `gate/up` → SwiGLU → `down` → 残差 | 只有 G10 两路并行，其余严格串行 |

对硬件和测试最直接的三点：

1. 关键路径是 `G0 → G1(qkv) → G2(conv) → G3 → G4 → G5 → G6 → G7 → G8 → G9 → G10 → G11 → G12 → G13`。`z`、`a`、`b` 三条支路不延长路径，但 `a`/`b` 的产物喂给 G5，必须与 G1 同批发射。
2. 测试向量的切分点就在 `[G]` 边界：组之间是完整的张量落盘点（形状见各行），组内是并行算子。做 per-op 对拍时，按 G0–G13 各存一份参考值，就能定位到具体算子。
3. 只有 G2 与 G5 跨 token，其余都是 token 内计算。所以跨 token 状态（`conv_state` 36 KB 加 `recurrent_state` 1 MiB）只在这两处读写，也是仅有两个无法靠并行隐藏的点。

---
---

# 第 4 章　四种层型的子模块清单（对照用）

第 3 章讲的是计算流，这一章把同样的内容整理成可查表的形式，用于权重排布、DMA 描述符和测试向量切分。

## 4.1 层型 A 的 14 个张量（18 份，完全一致）

| # | 张量名（相对 `layers.N.`） | 形状 | 参数量 | 计算形态 |
|---:|---|---|---:|---|
| 1 | `input_layernorm.weight` | [2048] | 2,048 | 归约 |
| 2 | `linear_attn.in_proj_qkv.weight` | [6144, 2048] | 12,582,912 | GEMM |
| 3 | `linear_attn.in_proj_z.weight` | [2048, 2048] | 4,194,304 | GEMM |
| 4 | `linear_attn.in_proj_b.weight` | [16, 2048] | 32,768 | GEMM（极小） |
| 5 | `linear_attn.in_proj_a.weight` | [16, 2048] | 32,768 | GEMM（极小） |
| 6 | `linear_attn.conv1d.weight` | [6144, 1, 4] | 24,576 | 深度卷积 |
| 7 | `linear_attn.A_log` | [16] | 16 | 逐元素 |
| 8 | `linear_attn.dt_bias` | [16] | 16 | 逐元素 |
| 9 | `linear_attn.norm.weight` | [128] | 128 | 归约（按 head） |
| 10 | `linear_attn.out_proj.weight` | [2048, 2048] | 4,194,304 | GEMM |
| 11 | `post_attention_layernorm.weight` | [2048] | 2,048 | 归约 |
| 12 | `mlp.gate_proj.weight` | [6144, 2048] | 12,582,912 | GEMM |
| 13 | `mlp.up_proj.weight` | [6144, 2048] | 12,582,912 | GEMM |
| 14 | `mlp.down_proj.weight` | [2048, 6144] | 12,582,912 | GEMM |
| | **合计** | | **58,814,624** | |

## 4.2 层型 B 的 11 个张量（6 份，完全一致）

| # | 张量名 | 形状 | 参数量 | 计算形态 |
|---:|---|---|---:|---|
| 1 | `input_layernorm.weight` | [2048] | 2,048 | 归约 |
| 2 | `self_attn.q_proj.weight` | [4096, 2048] | 8,388,608 | GEMM（Q + gate 合一） |
| 3 | `self_attn.k_proj.weight` | [512, 2048] | 1,048,576 | GEMM |
| 4 | `self_attn.v_proj.weight` | [512, 2048] | 1,048,576 | GEMM |
| 5 | `self_attn.o_proj.weight` | [2048, 2048] | 4,194,304 | GEMM |
| 6 | `self_attn.q_norm.weight` | [256] | 256 | 归约（按 head） |
| 7 | `self_attn.k_norm.weight` | [256] | 256 | 归约（按 head） |
| 8 | `post_attention_layernorm.weight` | [2048] | 2,048 | 归约 |
| 9 | `mlp.gate_proj.weight` | [6144, 2048] | 12,582,912 | GEMM |
| 10 | `mlp.up_proj.weight` | [6144, 2048] | 12,582,912 | GEMM |
| 11 | `mlp.down_proj.weight` | [2048, 6144] | 12,582,912 | GEMM |
| | **合计** | | **52,433,408** | |

> 全层 11 个张量全部无 bias（`attention_bias=false`）。

## 4.3 运行时状态（不在权重里，但决定 SRAM）

| 状态 | 形状 | 数量 | 精度 | 总大小 |
|---|---|---:|---|---:|
| DeltaNet `recurrent_state` | [16, 128, 128] /层 | 18 | FP32 | 18.87 MB |
| DeltaNet `conv_state` | [6144, 3] /层 | 18 | FP16 | 0.66 MB |
| Attention KV cache | [2, 2, S, 256] /层 | 6 | FP16 | 12,288·S 字节 |
| 视觉塔 KV | 无（一次前向） | — | — | 0 |
| 合计（S=4096） | | | | ≈ 70 MB |
| 合计（S=32768） | | | | ≈ 422 MB |

这张表是全模型最关键的一组数字。18 层 DeltaNet 的状态恒定 18.9 MB，而 6 层 attention 的 KV cache 随上下文线性增长，256K 时达到 3.2 GB。两者的存储策略必须分开设计。

---
---

# 第三部分　计算：每个算子做什么

---

# 第 5 章　通用算子库

全模型只用到 5 个通用算子：RMSNorm、RMSNormGated、SiLU/SwiGLU、因果深度卷积、部分 RoPE。后面任何一层都是这几个算子的排列组合。

它们都上不了矩阵引擎。原因相同：没有 K 维归约加规整访存的结构，要么是行内归约（RMSNorm），要么是跨 token 滑窗（conv），要么是跨 lane 置换（RoPE）。

有三条量化边界需要记住：增益是 `1+w` 而不是 `w`；`silu` 只能留在 FP16 走 LUT；RoPE 角度必须用 FP32 算，256K 上下文下建议 FP64 或整数 mod 2π。

decode 一个 token 的调用次数：归一 79 次，SwiGLU 24 次，深度卷积 18 次，RoPE 6 次。

---

## 5.1 RMSNorm（zero-centered，Qwen 特有）

**作用**：对残差流做尺度归一，压制层间幅值漂移。用 `1+w` 形式是为了让权重初始化时模块是恒等映射。
**调用**：`Qwen3_5RMSNorm.forward`（`modeling_qwen3_5.py:827`）→ `self._norm(x.float())`（`x.pow(2).mean(-1)` + `torch.rsqrt`）→ `output * (1.0 + self.weight.float())` → `.type_as(x)`
**公式**：

```
y = x / sqrt( mean(x², dim=-1) + ε ) · (1 + w),      ε = 1e-6
```
逐元素展开（n=2048）：`y_k = x_k · rsqrt( (1/n)·Σ_j x_j² + ε ) · (1 + w_k)`

**形状**：`[..., 2048]`（骨架/末层）、`[..., 256]`（attention 的 q_norm/k_norm，按 head）。decode 时就是 `[2048]` 一维向量。
**MAC 推导**：平方 2048 + 树形求和 2047 + 缩放 2048 ≈ **6.1 K MAC / 次**。全 token 共 79 次（24×2 骨架 + 18 DeltaNet gated-norm + 6×2 q/k_norm + 1 末层）→ 约 **0.48 M MAC**，占 1.9 GMAC 的 0.025%。但**延迟不可忽略**：每次都要一次 2048 宽、11 级（log₂2048）的全向量归约，归约完才能算 rsqrt，是全流水里最典型的“短算子 + 长依赖”。
**精度**：内部强制 **FP32**（`x.float()`），归约、rsqrt、乘 `1+w` 全在 FP32，最后一次 cast 回 `x.dtype`。注意代码顺序是 `(x_norm * w)` 在 FP32 里做完才降精度，比 Llama 的 `x.to(fp16)*w` 少一次舍入（源码注释专门说明了这点）。
**坑**：
1. **`1+w` 不是 `w`**。`weight` 初始化为 `torch.zeros(dim)`（`_init_weights` 里 `init.zeros_`），乘数是 `1+w`。若按普通 RMSNorm 把 `w` 直接当增益量化或做逐通道 scale，整层输出会平移一个常数倍，loss 立刻崩。硬件上应在权重加载时预计算 `g = 1 + w`，存成 FP16 增益表（2048×2B = 4 KB/层），不要每 token 再算一次加法。
2. **增益必须 FP16 以上**。`g` 是整向量统一相乘的系统性增益：GEMM 权重的量化误差会在 K=2048 的归约里被平均掉，`g` 的误差不会——它让每个 token、每一层都偏同样比例，79 级串联直接被放大。E4M3 的 3-bit 尾数在 `g∈[0.5,1.5]` 上给出 0.5%~6% 的系统增益误差，不可接受；FP16（2⁻¹¹ ≈ 4.9e-4）够用。
3. **方差必须 FP32 算**。`x²` 在 FP16 下超过 256 就溢出，而 Qwen 系列残差流存在数百量级的 outlier，直接在 FP16 里平方会出 inf，rsqrt 得 0，整层归零。这是最常见的"精度看起来够、实际 nan"的坑。
4. `ε=1e-6` 相对典型方差（O(1)~O(100)）可忽略，唯一作用是防全零向量除零，但不能省——量化后全零 block 会出现。
5. `Qwen3_5RMSNormGated` 用 `torch.ones` 加直接相乘，没有 `1+w`。同一个硬件归一单元要支持两种模式（zero-centered / ones-centered），靠配置位切换，不要默认同一种。

**rsqrt 的硬件实现（给 VPU 的规格建议）**
两种都可行，按面积/精度取舍：

| 方案 | 结构 | 精度 | 成本 |
|---|---|---|---|
| A. LUT + 1 次牛顿迭代 | 取 `a` 的指数 e 与尾数高 8 位查 2⁸ 表得 `y₀≈1/√m`，再 `y₁ = y₀·(1.5 − 0.5·a·y₀²)` | 初值 ~2⁻⁸，一次迭代 ~2⁻¹⁶（优于 FP16） | 1 次表读（256×2B=512 B）+ 3 FMA |
| B. 纯 LUT + 线性插值 | 2¹² 项表 + 1 次插值 | ~2⁻¹² | 8 KB 表，无迭代，1 拍出结果 |
| C. 直接 `1/sqrt` | 先开方（同样要 LUT+NR）再除法 | 最差 | 两级延迟 |

推荐方案 A：512 B 表加 3 拍，精度已超 FP16 需求。需要注意 rsqrt 的输入是 `mean+ε`，动态范围可达 1e-6~1e4，跨 10 个 binade，所以指数处理（偶数/奇数平分）必须做对，否则 e 为奇数时误差翻倍。

**归一化的调用点分布**（decode 每 token）：

| 位置 | 维度 | 次数 |
|---|---|---|
| `input_layernorm` / `post_attention_layernorm` | 2048 | 48 |
| attention `q_norm` / `k_norm` | 256 | 12 |
| DeltaNet `RMSNormGated` | 128 | 18 |
| `model.norm`（末层） | 2048 | 1 |
| **合计** | | **79** |

## 5.2 RMSNormGated（DeltaNet 专用）

**作用**：对 DeltaNet 每个 value head 的 128 维输出做归一，再用另一个分支 `z` 做 SiLU 门控。这是线性注意力里替代 softmax 的软归一。
**调用**：`Qwen3_5RMSNormGated.forward(hidden_states, gate)`（`modeling_qwen3_5.py:204`）→ 被 `Qwen3_5GatedDeltaNet` 在 `core_attn_out.reshape(-1, head_v_dim)` 后调用
**公式**：

```
o_h = RMSNorm(o_h) ⊙ silu(z_h)          # 按 head 独立，h = 0..15
    = o_h / sqrt( mean(o_h²) + ε ) · w ⊙ silu(z_h),   dim = 128, ε = 1e-6
```
注意这里是 `· w`（`weight=torch.ones`），不是 `1+w`。

**形状**：`o [B, S, 16, 128]`、`z [B, S, 16, 128]`；decode 时是 `[16,128]` 两张，合起来 `[2048]`。
**MAC 推导**：每 head 128 平方 + 127 求和 + 128 缩放 + 128 次 `silu` + 128 乘法 ≈ 0.51 K MAC；16 head = **8.2 K MAC/层**；18 层 = **0.15 M MAC/token**（0.008%）。纯延迟项：18 层 × 16 head = 288 个 128 宽归约（7 级）。
**精度**：归约与 `silu(gate)` 都在 FP32，但有两个细节与直觉不同：

- `hidden_states = self.weight * hidden_states.to(input_dtype)`：权重乘法发生在降精度之后（FP16×FP16），不在 FP32 里做；
- `hidden_states * ACT2FN["silu"](gate.to(torch.float32))` 这一步又回到 FP32，最后 `.to(input_dtype)`。

**坑**：
1. 归一化是 per-head 的 128 维，不是 per-token 的 2048 维。硬件若误按 2048 做一次归约，输出分布完全变了（16 个 head 的方差被混在一起）。归约宽度必须可配（128/256/2048 三档）。
2. `z` 分支的 `silu` 与 6.3 的 SwiGLU 是同一个单元，可以复用同一张 LUT，但这里输入是 FP32（`.to(torch.float32)`），LUT 的索引要按 FP32 尾数取，不能先降 FP16 再查表。
3. 归约在 `-1` 维（128）上做，`reshape(-1, 128)` 之后是 `[B·S·16, 128]`，这是一次布局变换。VPU 侧要么要求输入本来就是 head-major，要么付一次 16×128 的转置。

## 5.3 SwiGLU / SiLU

**作用**：门控激活，把 `gate` 分支压成 0~1 的软开关再乘到 `up` 分支上。
**调用**：`Qwen3_5MLP.forward` 里 `self.act_fn = ACT2FN["silu"]`（`hidden_act=silu`），即 `torch.nn.functional.silu`；`RMSNormGated` 里同一个 `ACT2FN["silu"]`。
**公式**：

```
silu(x) = x · σ(x) = x / (1 + e^(−x))
SwiGLU(g, u) = silu(g) ⊙ u          # 逐元素，6144 通道
```
**形状**：`[6144]`（decode）/ `[S, 6144]`（prefill）。
**MAC 推导**：6144 次 `exp` + 6144 次除（或 `1/(1+e^-x)` 一次倒数）+ 6144 乘 + 6144 乘 = **约 24.6 K 运算 / 层**，24 层 = 0.59 M/token。**算力可忽略，成本全在 `exp` 单元与延迟**。

**数值特性（决定硬件怎么实现）**

| 区间 | 行为 | 硬件处理 |
|---|---|---|
| `x → −∞` | `silu → 0⁻`，**全局最小值 −0.27846**（在 x ≈ −1.278） | 负半轴**有界**，永远打不穿 |
| `x ≤ −8` | `\|silu(x)\| ≤ 8·e⁻⁸ = 2.7e-3` | 直接钳到 **0**，绝对误差 ≤ 2.7e-3 |
| `x ≥ 8` | `σ(x) ≥ 1 − 3.35e-4` → `silu(x) = x(1−e⁻ˣ) ≈ x`，相对误差 ≤ 3.35e-4 | 直接取 **y = x**（FP16 在 x=8 处半 ulp 是 3.9e-3 > 2.7e-3，所以 **FP16 下 silu(8) 就等于 8.0**，是无损的） |
| `x ∈ (−8, 8)` | 唯一需要精度的区间 | LUT + 线性插值 |

**LUT 规格推导**：`|silu''| ≤ 0.5`（x=0 处取到）。线性插值误差 `≤ h²·max|f''|/8 = h²/16`。要 ≤ 1e-4 → `h ≤ 0.04` → 区间长 16 → 400 段 → 取 **512 项（h = 0.03125）**，误差 ≤ 6.1e-5，比 FP16 在 1.0 附近的半 ulp（2.4e-4）还小。
**结论：silu = 1 KB 表（512×FP16）+ 2 次比较（钳位）+ 1 次 FMA + 1 次 table read，3~4 拍。** 不需要 exp 单元，也不需要除法。这是 VPU 上性价比最高的一个单元。

**为什么不能量化到 FP8（三条独立理由，任一条都够）**

1. **负半轴的信息被压成个位数档位**。E4M3 是 1-4-3 布局，一个 binade 内只有 8 级、最大相对舍入 2⁻⁴ = 6.25%。silu 输出范围是 `[-0.278, +∞)`，若按 per-tensor scale（`s = max/448`，max 常被 20~40 的 outlier 撑到）取 `s ≈ 0.089`，则 `[-0.089, −0.278]` 这段只落在 2 个 binade 里，约 16~20 个可表示值；而 FP16 在同一段有 2¹¹ 级。负半轴承载的是"抑制多少"的连续信号，压到 20 档等于把门控退化成粗粒度开关。
2. **误差预算翻倍**。GEMM 用 MXFP8（per-32 block scale）时单元素相对误差 rms ≈ 2%~4%；再让 `silu(g)` 与 `u` 各自过一遍 FP8，乘积的相对误差 ≈ √3 × 3.6% ≈ 6%。`down` 的输出是 6144 项求和，随即变量的误差不会被平均掉：`δy/|y| ≈ ε`（因为 `|y| ~ ‖w⊙a‖`，`Var(δy) = ε²‖w⊙a‖²`）。也就是 6% 直接变成 MLP 输出的 6% 相对误差。这是误差预算推算，不是本模型的实测结果。
3. **没有任何收益**。逐元素算子工作在寄存器里，FP16→FP8 只省 12 KB SRAM/层，省 0 字节 DRAM 带宽。收益为零、精度有损。

工业界的做法与此一致（DeepSeek-V3 的 FP8 配方、vLLM 的 W8A8 实践）：FP8 只用于 GEMM 的输入，LayerNorm、softmax 和逐元素激活一律保留 BF16/FP16。这一条是经验规则，同样不是本模型实测。

**坑**：
1. `hidden_act=silu`，不是 `gelu`。视觉塔才是 `gelu_pytorch_tanh`（7.2 V⑧），两套激活不能共用一张表。
2. `F.silu` 在 PyTorch 里是 `x*sigmoid(x)`，不是 `x*tanh(softplus(x))`。有些实现用后者，浮点上不等价，差约 1e-3。
3. 精度必须留在 FP16：负半轴 −0.278 的细节在 BF16（8-bit 尾数）下只剩约 1e-3 分辨率，可以接受，FP8 不可接受。silu 的输入输出都留在 FP16，只在 DMA 送进 `down` 的矩阵引擎时现场量化。

## 5.4 因果深度卷积 + SiLU（DeltaNet 专用）

**作用**：在 DeltaNet 的 q/k/v 上做 4-tap 的短程混合，把最近 4 个 token 的局部信息补进递推状态，弥补线性注意力对局部顺序不敏感的问题。
**调用**：decode → `causal_conv1d_update(hidden_states, conv_state, weight, bias, activation="silu")`（`modeling_qwen3_5.py:235`）；prefill → `causal_conv1d_fn`（同文件 255 行）。二者最终都落到 `F.conv1d(..., groups=hidden_size, padding=...)` + `ACT2FN["silu"]`。
**公式**：

```
y[t, c] = silu( Σ_{j=0}^{3} w[c, j] · x[t − 3 + j, c] )
```
（`F.conv1d` 是互相关：`w[0]` 乘的是最老的 `x[t−3]`，`w[3]` 乘 `x[t]`。若按 `x[t−j]` 索引，权重需要翻转，这是移植时最常见的静默错误。）
**形状**：`x [1, 6144, S]`、`weight [6144, 1, 4]`、`conv_state [1, 6144, 3]`；输出 `[1, 6144, S]`。
**MAC 推导**：`6144 × 4 = 24,576 MAC / token / 层`；18 层 = **0.44 M MAC/token**（0.02%）。访存：读 state 36 KB + 写 state 36 KB + 读 x 12 KB + 写 y 12 KB ≈ 96 KB/层，18 层 ≈ 1.7 MB/token。这里的访存比算力重要得多。
**精度**：`conv_state` 以 hidden dtype（FP16）保存，`hidden_states_new.to(weight.dtype)` 后再卷积，最后 `out.to(hidden_states.dtype)`。卷积本身是 4 项 FP16 累加，误差可忽略；但状态跨 token 累积（滚动窗口），FP16 舍入会一代代传下去，只是 4 tap 的窗口不会放大误差。
**坑**：
1. depthwise：`groups=conv_dim=6144`，调用时 `weight.squeeze(1)` 得到 `[6144,4]`（源码 `:578`）。通道之间完全不混合，所以这不是 GEMM，而是 6144 条独立的 4-tap FIR，放矩阵引擎上会浪费 99% 的 MAC 阵列。
2. causal 的实现方式：`update` 路径把 `[state(3) ‖ x(seq)]` 拼起来，`padding=0` 卷积后取末尾 `seq_len` 个；`fn` 路径是 `padding=3` 后取前 `seq_len` 个。两条路径等价，但索引方向相反，硬件实现选一种即可（推荐"左补 3、取前 S"的 prefill 形式，decode 就是 S=1 的特例）。
3. `conv_state` 的滚动顺序：`conv_state.copy_(hidden_states_new[:, :, -state_len:])`，是原地写回。这意味着 conv 单元必须在读完 state 之后、处理下一个 token 之前完成写回，中间不能插入 DMA 重排，否则状态错位。三份 state（q/k/v 拼在 6144 里）共用同一个 buffer，按 2048 边界切。
4. 激活已经融合在算子里（`if activation is not None`），不要在外面再补一次 silu，重复激活会让输出幅值整体压低。
5. `bias=False`（`:508–515`）：`conv1d` 没有 bias，`causal_conv1d_fn` 的 `bias` 形参恒为 `None`。RTL 里不要留一个永远为 0 的 bias 加法。
6. 投机解码时 conv_state 必须能回滚（见 6.4）。它是 4-tap 滑动窗口，被拒绝的 token 已经把旧样本挤出窗口，无法靠截断恢复，只能快照。

## 5.5 部分 RoPE（attention 专用）

**作用**：给 Q/K 注入位置信息。只旋转 head_dim=256 的前 64 维，后 192 维原样透传（`partial_rotary_factor=0.25`）。
**调用**：`Qwen3_5RotaryEmbedding.forward(x, position_ids)`（147–188 行）产出 `cos/sin`；`apply_rotary_pos_emb(q, k, cos, sin)`（`modeling_qwen3_5.py:661`）→ `rotate_half`（653 行）。
**公式**：

```
θ_i = pos / 10000^(2i/64) = pos / 1e7^(i/32),   i = 0..31
q'_i      = q_i      · cos θ_i − q_{i+32} · sin θ_i
q'_{i+32} = q_{i+32} · cos θ_i + q_i      · sin θ_i
q'_j = q_j                              ,   j = 64..255（透传）
```
**形状**：`q [1, 8, S, 256]`、`k [1, 2, S, 256]`；`cos/sin [S, 64]`（32 个不同角度被 `cat` 复制成 64 份，所以 `cos[i] == cos[i+32]`）。
**MAC 推导**：每对 4 次 MAC。q：8 head × 32 对 × 4 = 1024；k：2 head × 32 对 × 4 = 256，合计 **1,280 MAC/token/层**，6 层 = 7.7 K MAC。算力可以忽略，成本全在 `sin/cos` 与跨 lane 置换。
**精度**：角度在 FP32 里算（`position_ids.float() * inv_freq`，`inv_freq` 也是 FP32 buffer），算完 `cos.to(dtype=x.dtype)` 降到模型 dtype。旋转本身 FP16 可接受。
**坑**：
1. **half-split 不是 interleaved**。`rotate_half` 把向量劈成前后两半再配对：`(q_0, q_32), (q_1, q_33), …`。这是 GPT-NeoX 布局，不是 GPT-J 的 `(q_0,q_1),(q_2,q_3)`。按 interleaved 实现不会报错，只会让模型答非所问。硬件上这是一次跨 32 距离的置换，需要 VPU 有 permute/butterfly 网络，或者直接按 half-split 布局存放 q/k。
2. **`pos` 必须是整数、角度必须 FP32**。在 262144 位置上，FP16 的 `pos` ulp 是 0.25，角度误差 0.25 rad，旋转完全失效。这是长上下文下最隐蔽的精度坑。
3. **cos/sin 表不要预存**。存储量：512 B/位置（64×2×FP32），S=4096 是 2.1 MB、S=32768 是 16.8 MB、S=262144 是 134 MB。现算只需每 token 32 对 sin/cos，约 64 次超越函数，≈1.3 µs @1 GHz，占 6.26 ms token 时间的 0.02%。所以 decode 现算当前 pos（512 B 缓冲），prefill 现算一个 S 长窗口；只有当三角函数单元极度稀缺时才考虑存表，且只存 32 个角度（省一半）。
4. **mRoPE 在纯文本下是恒等变换**。代码里无条件 `position_ids.expand(3,…)` 再按 `mrope_section=[11,11,10]` 做 `recomposition_frequencies` 散射；文本输入三组 pos 相同，散射等于复制。纯文本推理可以整段跳过，只保留普通 RoPE 路径。但一旦要支持图像，`[11,11,10]` 的交错必须原样实现（32 = 11+11+10，正好用满 64 维的一半）。
5. cos/sin 要 broadcast 到 8 个 q head：`cos.unsqueeze(1)`。8 个 head 共享同一份 512 B，不要每个 head 各算一遍。

---
---

# 第 6 章　四种层型的逐算子拆解

这一章把 4 种层型拆成 40 张算子卡（A①–A⑭ / B①–B⑫ / M1–M6 / D1–D8）。每张卡的结构固定：作用、调用函数（含行号）、公式、形状、MAC 推导、精度、坑。

两个结论先放在前面。层型 A 的算力有 98.6% 在 GEMM、1.4% 在串行，但延迟上那 1.4% 决定一切，delta 递推是 decode 唯一真正的关键路径。层型 B 的成本重心不在权重而在 S：S=4096 时 `QKᵀ` 加 `A·V`（16.78 M）已经超过四个投影之和（14.68 M）。

行号全部对照 `transformers` main 分支的 `modeling_qwen3_5.py`（本地副本 `/tmp/m35.py`，87,748 B，2026-10 抓取）；fla kernel 的内部调用链写在各卡的调用字段里。

**记号**：`B`=batch，`S`=序列长，`d`=2048（hidden），`M`=本次前向的 token 数（decode 为 1，prefill 为 S）。MAC = 一次乘加（2 FLOP）。形状默认按 decode（B=1, M=1）写，prefill 把 M=1 换成 M=S。

**算子编号索引**

| 层型 | 编号 | 算子序列 | 归属速查 |
|---|---|---|---|
| A　Gated DeltaNet（18 层） | A①–A⑭ | RMSNorm → qkv/z/b/a 四投影 → 因果卷积 → split → L2 → β/g → delta 递推 → 门控归一 → out_proj → 残差 → MLP | 6.1.15 |
| B　Gated Attention（6 层） | B①–B⑫ | RMSNorm → q(双宽)/k/v → qk-norm → RoPE → KV 追加 → QKᵀ → softmax → A·V → 门控 → o_proj → 残差 | 6.2.4 |
| C　SwiGLU MLP（24 份） | M1–M6 | RMSNorm → gate/up → SwiGLU → down → 残差 | 6.3 M6 |
| D　MTP（1 份） | D1–D8 | 两路 norm → concat → fc → 全注意力层 → norm → 共享 lm_head → 投机解码数学 → 回滚 | 6.4 D8 |

计数口径：A 的归约按乘加数记（平方 2048 + 缩放 2048），B 的归约按元素数记。两者都不到 0.02%，不影响任何结论，这也是层型 A 的合计比 B 多出 8 K 的原因。

---

## 6.1 层型 A：Gated DeltaNet（18 层）

层型 A 的完整链路是 `RMSNorm → 4 个投影 → 因果深度卷积（跨 token）→ 拆 q/k/v → L2 归一 → 门控 delta 递推（跨 token 状态）→ 门控归一 → out_proj → 残差 → MLP`。与 6.2 的 Gated Attention 相比，它没有 KV cache、没有 softmax、没有位置编码，取而代之的是每层 1 MiB 的 fp32 循环状态（`16×128×128`）。

本节符号：`H_k=16`（k head 数），`H_v=16`（v head 数），`D_k=128`（k head 维），`D_v=128`（v head 维）；`B`、`S`、`d`、`M` 同本章前言。

---

### A① 前置 RMSNorm

**作用**：对残差流做逐 token 的全维归一，把动态范围压到 `~N(0,1)`，为后面 4 个投影提供稳定输入。
**调用**：`self.input_layernorm(hidden_states)` → `Qwen3_5RMSNorm.forward`（`m35.py:837`），hub kernel 名为 `RMSNormZeroCentered`。
**公式**：

```
μ₂   = (1/d) · Σ_{i=1..d} x_i²                       d = 2048
rstd = 1 / sqrt(μ₂ + eps)                            eps = 1e-6
y_i  = x_i · rstd · (1 + w_i)                        w 为可学习参数，零初始化
```

注意 **不是** `y = x/rms * w`，而是 `* (1+w)`：`w` 初始化为 0（`:832` `torch.zeros(dim)`），等价于单位增益。硬件若按 Llama 的 `w` 语义实现，上电即全零输出。

**形状**：`[B, S, 2048] → [B, S, 2048]`
**MAC 推导**：平方和 2048 次乘加 + 2048 次缩放乘 = **≈4,096 MAC**（占单层 0.007%，可忽略；但它是**每 token 2×2048 元素的读写**，带宽不可忽略）。
**精度**：**必须 FP32 累加**。2048 项平方和在 BF16 下（8 bit 尾数）相对误差 ≈ `2^-8·sqrt(2048) ≈ 0.18`，直接让 rstd 偏 9%；源码本身也是 `self._norm(x.float())`。输出可按激活 dtype（BF16）落盘，`1+w` 用 FP32 乘。
**坑**：
- `eps` 加在 **mean 之后**（`mean(x²)+eps`），与 A⑥ 的 L2 norm（`eps` 在 `sum` 之内，且不带 `mean`）**不是同一个公式**，不能共用同一个 RDU 微码。
- 归约是 **2048 长**，跨 16 个 head 边界；而 A⑪ 的归约是 **128 长**。两者复用同一个 reduction tree 时要注意 lane 映射。
- 这是层内**第一个**访存点：读 4 KB（BF16）+ 读 4 KB 权重（FP32 8 KB），写 4 KB。

---

### A② `in_proj_qkv` 融合投影

**作用**：一次 GEMM 同时产出 q、k、v 三路（共享输入），是层内最大的单个 GEMM。
**调用**：`self.in_proj_qkv(hidden_states)`，`nn.Linear(2048, 6144, bias=False)`（`:530`）。
**公式**：

```
[qkv]_o = Σ_{i=1..2048} W_qkv[o, i] · x_i ,   o = 1..6144
q = qkv[0 : 2048]          k = qkv[2048 : 4096]        v = qkv[4096 : 6144]
```

维度自洽：`key_dim = D_k·H_k = 128×16 = 2048`，`value_dim = D_v·H_v = 128×16 = 2048`，故 `conv_dim = key_dim×2 + value_dim = 2048+2048+2048 = 6144` ✔（与 `:507` 一致）。

**形状**：`[B,S,2048] → [B,S,6144]`（decode：`[1,2048] → [1,6144]`）
**MAC 推导**：

```
M × K × N = 1 × 2048 × 6144 = 12,582,912 MAC = 12.58 M     （占单层 21.1%）
```

**精度**：MXFP8 权重 + MXFP8 激活、**FP32 累加**。K=2048 的累加链，FP16 累加会引入 `sqrt(2048)·2^-11 ≈ 2.2%` 的相对误差；FP32 累加器误差 `sqrt(2048)·2^-24 ≈ 2.7e-6`，安全。q/k 分支后接 L2 归一（尺度不变），对**整体缩放**误差免疫；但 v 分支直通状态，逐元素相对误差需 ≤ 2^-8。
**坑**：
- 上游是 `[B,S,2048]` 行主序，下游 conv 要 `[B,6144,S]`，中间有 `transpose(1,2)`（`:555`）。**这个 transpose 是 stride 置换，不是拷贝**，但会让 `F.conv1d` 内部做一次 `contiguous()` —— decode 时等于多搬 12 KB，属实测可见的隐藏开销。
- 输出缓冲要能按 `[0:2048] / [2048:4096] / [4096:6144]` 三段切，且三段消费者不同（q/k 要 L2 归一，v 不要）；建议 DMA 就按 2048 边界分三段描述符，省掉后面 `torch.split` 的地址计算。
- `H_k = H_v = 16`，**GQA 比为 1**，`:608` 的 `repeat_interleave` 分支在本模型不会执行。若照抄 Qwen3-Next 的 32:16 配置会形状错。

---

### A③ `in_proj_z` / `in_proj_b` / `in_proj_a` 三个辅助投影

**作用**：`z` 是输出门（逐元素门控 value 维），`b` 产出写入门 β，`a` 产出衰减门 logits。
**调用**：`self.in_proj_z / in_proj_b / in_proj_a`（`:531–533`），三者都是 `bias=False` 的 `nn.Linear`。
**公式**：

```
z_o = Σ_i W_z[o,i]·x_i ,  o = 1..2048        → reshape [B,S,16,128]
b_h = Σ_i W_b[h,i]·x_i ,  h = 1..16
a_h = Σ_i W_a[h,i]·x_i ,  h = 1..16
```

**形状**：`[B,S,2048] → z:[B,S,2048] / b:[B,S,16] / a:[B,S,16]`
**MAC 推导**：

```
z : 2048 × 2048 = 4,194,304
b : 2048 ×   16 =    32,768
a : 2048 ×   16 =    32,768
合计 = 4,259,840 MAC = 4.26 M                （占单层 7.1%，其中 z 占 98.5%）
```

**精度**：
- `z`：走门控乘法，MXFP8 可接受（误差直接乘在输出上，2^-8 量级可见但对 perplexity 不敏感）；建议 FP16 更稳。
- `b`：**不要用 FP8**。`β = σ(b)`，`dβ/db = β(1-β) ≤ 0.25`；`b` 的 FP8(E4M3) 相对误差 ~6% → 绝对误差 ~0.06·|b| → `Δβ ≈ 0.015·|b|`。而 β 是"写入多少新信息"的开关，1.5% 的 β 漂移在 18 层 × 每层 16 头的递推里会累积成可测的漂移。用 FP16（`Δβ ≈ 0.0005`）。
- `a`：进入 `softplus` 再进 `exp`，**必须 FP16 以上**，且 softplus 的输入偏置 `dt_bias=1` 意味着 `a≈0` 附近是工作点，此处 `softplus'(1)=σ(1)=0.73`，无饱和保护。
**坑**：
- `N=16` 的 GEMV 在矩阵引擎上**列利用率极低**：若 MMA tile 为 `128×128`，只用 16 列 → 12.5%。绝对量只有 0.0655 M MAC（占 0.11%），**真正的代价是独立的 kernel 启动 + 每次 32 KB 权重搬运**，不是算力。
- **推荐做法**：离线把 `W_b`(16×2048) 与 `W_a`(16×2048) 上下拼接成 `W_ba [32, 2048]`，一次 GEMV 出 32 维，再在 VPU 上切 16/16；或进一步把 `[W_qkv; W_z; W_b; W_a]` 打包成 `[6176, 2048]` 单次投影（需离线重排权重，属于权重重打包，非等价于原图，需在加载时做一次）。后者把 4 次 GEMV 合成 1 次，权重 12.65 MB 连读，decode 带宽收益显著。
- `z` 必须在 `A⑪` 之前与 `core_attn_out` 对齐到同一 `(-1, 128)` 展平顺序（`:644–645`），否则门控错位——而且**不会报错**，只会掉点。

---

### A④ `causal_conv1d_fn`：深度因果卷积 + SiLU 融合

**作用**：在 q/k/v 拼接后的 6144 条通道上各做一次 4-tap 因果卷积，让每个通道混入**过去 3 个 token** 的信息，再逐元素 SiLU。这是整个 DeltaNet 里除递推外**唯一的跨 token 算子**。
**调用**：
- prefill / 非单 token：`causal_conv1d_fn(mixed_qkv, weight.squeeze(1), bias, activation="silu")`（`:578`），fla 的 `causal_conv1d` 包。
- decode 单 token：`causal_conv1d_update(...)`（`:565`），fla 的 `causal_conv1d_update`，**就地更新 conv_state**。

**公式**（`K=4`，`c` 为通道，`t` 为时刻）：

```
y[c, t] = silu( Σ_{j=0}^{3} W[c, j] · x[c, t + j − 3] )        x[·, <0] ≡ 0
        = silu( W[c,0]·x_{t−3} + W[c,1]·x_{t−2} + W[c,2]·x_{t−1} + W[c,3]·x_t )
silu(u) = u · σ(u) = u / (1 + e^{−u})
```

因果性来自**左侧 pad 3**（`padding = kernel_size − 1 = 3`，`:264`），输出再切 `[:, :, :seq_len]`（`:272`）。等价说法：位置 `t` 只看 `t−3..t`，**永不看未来**。

**两种执行路径**：

| 路径 | 条件（源码） | 做法 |
|---|---|---|
| prefill | `seq_len > 1` 或正在录制历史 | 把 conv_state 里最近 3 帧接到序列左侧 → `F.conv1d(padding=3)` → 输出长度 `S+3` → 取 `[:, :, -S:]`（`:588`） |
| decode | `conv_state is not None and seq_len == 1 and not is_recording_past`（`:563`） | `cat([state(3), x(1)]) → [B,6144,4]`，`conv1d(padding=0)` 出 1 个点，`state.copy_(window[-3:])` |

**conv_state 滚动更新**（decode 分支逐行）：

```
window = concat(conv_state[B,6144,3], x_t[B,6144,1]) → [B,6144,4]
conv_state ← window[:, :, -3:]                          # 就地 copy_，滑窗右移
y_t = silu( Σ_j W[:, j] · window[:, :, j] )
```

即 `conv_state` 恒为 **最近 3 个 token 的输入**（不是输出，是 conv 的输入），`[B, 6144, 3]`，BF16 下 36 KB/层。

**形状**：`[B, 6144, S] → [B, 6144, S]`（decode：`[1,6144,1]`，状态 `[1,6144,3]`）
**MAC 推导**：

```
6144 通道 × 4 tap = 24,576 MAC/token = 0.0246 M      （占单层 0.041%）
+ 6144 次 silu（1 次 exp + 1 次 div + 2 次 mul ≈ 4 次超越/算术）
```

**带宽推导**（decode，这才是瓶颈）：

```
权重：6144 × 4 = 24,576 元素 × 2 B = 48 KB      （可常驻 SRAM，不必每 token 重读）
状态：6144 × 3 = 18,432 元素 × 2 B = 36 KB 读 + 36 KB 写
算术强度 ≈ 24,576 MAC / (48 KB+72 KB) ≈ 0.2 MAC/B  → 纯带宽受限
```

**精度**：
- 卷积本身 4 项累加，FP16 足够（误差 `sqrt(4)·2^-11 ≈ 0.1%`）。
- **SiLU 建议 FP32 或查表**：`silu` 在 `u ≈ −8` 处输出 `−2.7e-3`，FP16 下 `e^{8}=2981` 尚可，但 `u < −11.1` 时 `e^{-u}` 在 FP16 溢出为 `inf`，`u/inf = −0`，函数值被压成 0（真值 `−1.7e-5`）。这属"可接受但不干净"的误差；BF16 指数范围同 FP32，无此问题。
- 卷积输出动态范围：`|W|` 典型 `O(0.5)`，4 tap 叠加后 `|y| ~ 2`，无溢出风险。

**坑**：
1. **权重是 `[6144, 1, 4]`**，调用时 `self.conv1d.weight.squeeze(1)` → `[6144, 4]`；硬件若按 `[6144,4]` 布局存，注意 squeeze 后 stride 仍是 `(4,4,1)`，第 0 维 stride=4 与第 1 维 stride=1 不冲突，可零拷贝。
2. **`bias=False`**，所以 `self.conv1d.bias` 是 `None`，`causal_conv1d_fn` 的 `bias` 形参恒为 `None`。别在 RTL 里留一个永远为 0 的 bias 加法。
3. **`conv_state` 必须在卷积前更新**（`:246–247` 先 `cat` 再 `copy_` 再 conv）。若流水线为了隐藏延迟把 `copy_` 挪到 conv 之后，会在 `seq_len>1` 的投机解码路径上读到被覆盖的窗口。
4. **padding token 必须先掩掉**：`apply_mask_to_padding_states`（`:543`）在整层最前面把 padding 位置的 hidden 置 0；否则 padding 的激活会被卷进 `conv_state`，污染后续所有 token（对应 mamba issue #66）。
5. **`is_recording_past` 分支**：投机解码/EAGLE 类场景会强制走 prefill 路径重算整个前缀，从 `O(1)` 退化到 `O(S)`。测试用例要专门覆盖这条路径。
6. **`transpose(1,2)` 的双向搬运**：`:555` 转成 channels-first，`:590` 转回。两次都是 stride 置换，但 conv 内核通常要求 `[B,C,S]` 连续，实际会发生一次物化拷贝。
7. `conv_state` 的 dtype 由 `cache_utils` 决定（本文件不可见）。若以 BF16 存储，则每 token 的 `cat` 会把 BF16 状态与 BF16 输入拼成 BF16 再转 `weight.dtype`（`:246` 的 `.to(weight.dtype)`），这一转换在 FP16 权重下无害，但会引入一次额外的精度截断。

---

### A⑤ `torch.split` → q/k/v + reshape 成 `[16, 128]`

**作用**：把 6144 维按 `[2048, 2048, 2048]` 切成 q/k/v，并把每路 reshape 成 `[S, 16, 128]`（头维显式化）。
**调用**：`torch.split(mixed_qkv, [key_dim, key_dim, value_dim], dim=-1)`（`:591`）+ 三个 `reshape`（`:601–603`）。
**公式**（纯索引，无算术）：

```
query[b,s,h,:] = qkv[b,s, 128h : 128h+128]                 h = 0..15
key  [b,s,h,:] = qkv[b,s, 2048 + 128h : 2048 + 128h + 128]
value[b,s,h,:] = qkv[b,s, 4096 + 128h : 4096 + 128h + 128]
```

**形状**：`[B,S,6144] → 3 × [B,S,2048] → 3 × [B,S,16,128]`
**MAC 推导**：**0 MAC**。这是纯 view（`split` 产生 stride 视图，`reshape` 在最后一维连续时不拷贝）。
**精度**：不适用（无算术）。
**坑**：
- `split` 出来的是**非连续视图**（行 stride 仍是 6144），`reshape` 到 `[B,S,16,128]` 时因为 `2048 = 16×128` 且最后一维连续，**不会触发拷贝**；但下游 kernel 若假设 `[B,S,H,D]` 连续，会一次性物化 3×12 KB。建议在 A④ 输出处就直接按 128 粒度分块 DMA，绕过这两个视图。
- `h` 的语义在 q/k 与 v 上**是同一组 16 个头**（`H_k = H_v = 16`），所以 `h` 索引可直接对齐；若换成 GQA 比 >1 的模型（Qwen3-Next），`repeat_interleave` 会让 q/k 的头数变成 32，索引映射随之改变。
- **顺序不能错**：`conv_dim` 的排布是 `q|k|v`，与 `:591` 的 split 顺序、与权重的行排布三者强耦合。权重重打包（如 A③ 建议的 `[6176,2048]` 拼接）必须保持 qkv 段仍在前 6144 行。

---

### A⑥ L2 归一化（kernel 内 `use_qk_l2norm_in_kernel=True`）

**作用**：把每个 head 的 q、k 投到单位球面上，使 `q·k ∈ [−1, 1]`，从而 `(I + β K Kᵀ)` 的特征值有界，delta rule 的逆求解数值可控。
**调用**：**不在 Python 层**。`torch_chunk_gated_delta_rule(..., use_qk_l2norm_in_kernel=True)`（`:634`）/ `torch_recurrent_gated_delta_rule(..., True)`（`:621`）在 kernel 内部调 `l2norm(x, dim=-1, eps=1e-6)`（`:280–283`）；Triton 侧为 `b_k / tl.sqrt(tl.sum(b_k*b_k) + 1e-6)`（fla `fused_recurrent.py:120`）。
**公式**：

```
q̃[h,:] = q[h,:] / sqrt( Σ_{i=1..128} q[h,i]² + 1e-6 )        ‖q̃[h,:]‖₂ = 1
k̃[h,:] = k[h,:] / sqrt( Σ_{i=1..128} k[h,i]² + 1e-6 )
随后统一缩放： q̂ = q̃ · d^(−1/2) = q̃ / 11.3137      （`:333` / `:454`）
```

**为什么不是 RMSNorm**：

| | L2 norm（本算子） | RMSNorm（A①/A⑪） |
|---|---|---|
| 分母 | `sqrt(Σ x² + eps)` = `‖x‖₂` | `sqrt((1/d)Σ x² + eps)` = `rms` |
| 归一后范数 | 恰好 **1**（尺度无关） | `sqrt(d)` |
| 可学习增益 | **无** | 有（A① 为 `1+w`，A⑪ 为 `w`） |
| eps 位置 | **sqrt 之内**（加在平方和上） | 加在 **mean 之后** |
| 归约域 | 单头 128 维 | A① 为 2048 维、A⑪ 为 128 维 |

差一个 `sqrt(128)` 的常数：`RMSNorm(x) = sqrt(128)·L2norm(x)`。**两者数值上可互相换算，但 eps 语义不同**（`1e-6` 在 `Σx²` 上是 `1e-6/128` 的相对量级，在 `mean(x²)` 上是 `1e-6` 的相对量级，差 128 倍）。硬件不要复用同一份微码。

**形状**：`[B,S,16,128] × 2 → [B,S,16,128] × 2`（原地或另开缓冲）
**MAC 推导**：

```
平方和：2 路 (q,k) × 16 头 × 128 维 = 4,096 MAC
缩放  ：2 × 16 × 128                = 4,096 乘
合计 ≈ 8,192 MAC = 0.008 M                                （占单层 0.014%）
```

**精度**：**必须 FP32**。128 项平方和在 BF16 下相对误差 `2^-8·sqrt(128) ≈ 4.4e-2`（4.4%！），`rsqrt` 后再放大一倍；FP32 为 `sqrt(128)·2^-24 ≈ 6.7e-7`。源码注释（`:328`）明确"in fp32 to match the FLA library"。
**坑**：
- **L2 归一使 q/k 对整体缩放免疫**：MXFP8 的 block-scale 量化误差中"每 head 一个公共缩放"那部分会被完全吸收。真正保留下来的是**逐元素相对误差**（8 bit 尾数 → `2^-8`）。这对 q/k 是有利的，可作为"q/k 投影可以用 MXFP8"的正式论据。
- **必须在 FP32 里算，且必须在 `[B,S,16,128]` 布局上算**（最后一维 128 连续）。若在 `[B,S,2048]` 上按 2048 做 L2，会把 16 个头混成一个球面，模型直接崩。
- `eps` 在 `sqrt` 内意味着 `x ≡ 0` 时输出仍是 0（不是 NaN）；但 `Σx²` 若在 FP16 下溢出（`>65504`，即 `|x| > 22`），会得到 `inf` → `x/inf = 0`，静默清零。这是 FP16 实现的典型故障模式。
- 缩放 `d^(-1/2) = 0.08839` 可以在 L2 之后合并进 `rsqrt`（`rstd × 0.08839`），省一次 2048 元素的乘；顺序上等价（标量可结合）。

---

### A⑦ `beta = b.sigmoid()`

**作用**：把 16 维 logits 压到 `(0,1)`，作为 delta rule 的"学习率/写入门"——控制当前 token 覆盖旧状态的程度。
**调用**：`beta = b.sigmoid()`（`:605`）。fla kernel 亦可融合（`fused_recurrent.py` 的 `APPLY_BETA_SIGMOID`、`wy_fast` 的 `fused_beta_sigmoid`），但 transformers 的 fallback 路径在 Python 层先算好。
**公式**：

```
β_h = σ(b_h) = 1 / (1 + e^{−b_h}) ,   h = 1..16，广播到 128 维 v 行与 128 维 k 行
```

语义：`β→0` 表示本 token 不写入状态（状态只衰减）；`β→1` 表示沿 `k` 方向**完全覆盖**旧记忆。这正是 delta rule 相对普通线性注意力的关键——它做的是"最小二乘修正"而非"无脑累加"。

**形状**：`[B,S,16] → [B,S,16]`（在 kernel 内 `unsqueeze(-1)` 成 `[B,S,16,1]` 参与广播）
**MAC 推导**：16 次 sigmoid（1 exp + 1 add + 1 div ≈ 3 次超越/算术）→ **≈16 MAC 当量，0.000016 M**，可完全忽略。
**精度**：**FP16 起步，建议 FP32**。论据：
- `dβ/db = β(1−β) ≤ 0.25`，即 `b` 的绝对误差被**压缩 4 倍**才传到 β —— 这使 sigmoid 本身对噪声不敏感（好事）。
- 但 β 会**乘在 `(v − Sᵀk)` 上**，而 `v − Sᵀk` 在模型收敛后是"残差"（数值上远小于 `v`）。β 的 1% 相对误差 → 残差的 1% 误差 → 每步写入量偏差 1%，1000 步后状态漂移可达 `1 − 0.99^1000 ≈ 1`（完全不同的状态）。所以 β 需要 FP16（`2^-11 ≈ 5e-4`）以上，**不能用 FP8**。
- FP32 中 `σ` 的定点实现（查表 + 线性插值，16 项）即可满足 `2^-12` 精度，成本可忽略。
**坑**：
- **β 是 per-value-head 的标量，不是 per-channel**。硬件若按 128 维向量做门控（多 128 倍存储与带宽），功能正确但浪费；反之若把 16 个头的 β 弄成 1 个（广播错），则是静默错误。
- `b.sigmoid()` 在 `|b| > 20` 时会饱和到 0/1，此时梯度为 0，训练侧会"卡死"；推理侧无害，但**测试向量要覆盖 `b = 0, ±10, ±30`**，验证 sigmoid 的饱和行为与 FP16 一致。
- 若走 fla 的融合路径（`APPLY_BETA_SIGMOID=True`），β 在 kernel 内计算，Python 侧传入的是**原始 logits**。两条路径的输入语义不同，接口测试要分开建 golden。

---

### A⑧ `g = −exp(A_log) · softplus(a + dt_bias)`：对数域衰减率

**作用**：为每个 value head 产生一个 **≤0 的对数衰减率**，控制循环状态在时间轴上的遗忘速度。这是 Mamba 式连续时间离散化的产物。
**调用**：`g = -self.A_log.float().exp() * F.softplus(a.float() + self.dt_bias)`（`:607`）。
**公式**：

```
A_h    = exp(A_log_h) ∈ (0, +∞)         初始化 A_log = log(U(0.01,16))  → A_h ∈ [0.01, 16]
Δ_h    = softplus(a_h + dt_bias_h) = log(1 + e^{a_h + dt_bias_h})       dt_bias 初始化为 1
g_h    = − A_h · Δ_h  ≤ 0                （log 域衰减率）
状态衰减因子 = exp(g_h) = exp(−A_h·Δ_h) ∈ (0, 1]
```

**为什么是"对数域"**：状态更新是 `S_t = e^{g_t} ⊙ S_{t−1} + ΔS_t`。若直接存 `λ_t = e^{g_t}`，则每个 token 都要先算一次 `exp`，且分块算法里的"块内累积衰减"`exp(Σ g)` 无法用 `cumsum` 得到（乘积无法前缀求和）。存 `g` 后，块内累积衰减 = `cumsum(g)`（加法），最后一次 `exp` 即可——这就是 A⑩ 里 `cum_decay = g.cumsum(dim=3)`（`:360`）的全部理由。**这是把一个 O(T) 次 exp 降为 O(T/64) 次的关键重参数化**。

**Mamba 式离散化的含义**：连续时间 SSM 为 `dS/dt = −A⊙S + inflow`，零阶保持离散化后 `S_t = e^{−A·Δ_t} S_{t−1} + ...`，其中 `Δ_t` 是"步长"，由输入 `a` 动态预测（selective）。`dt_bias` 是步长的静态偏置，`A_log` 是可学习的"遗忘速率"（Mamba 论文里约束为正，故用 `log` 参数化）。

**形状**：`a:[B,S,16] , dt_bias:[16] , A_log:[16] → g:[B,S,16]`（kernel 内广播到 `[B,S,16,1]`）
**MAC 推导**：

```
16 头 × (1 次 exp + 1 次 softplus(≈exp+log1p) + 1 次 mul) ≈ 16 × 3 = 48 次超越/算术
折算 ≈ 0.00005 M MAC 当量，可忽略（但它是 48 次超越函数，VPU 上约 48×8 = 384 cycle）
```

**精度**：**必须 FP32，且这是全层最强的 FP32 约束**。三重论据：

1. **源码注释直接说明**（`:606`）："If the model is loaded in fp16, without the `.float()` here, A might be -inf"。机制（推断，源码未展开）：`A_h·Δ_h` 在 FP16 中若乘积 `> 65504` 即溢出为 `+inf`，取负后 `g = −inf`，下一句 `exp(g) = 0`，**整个 head 的记忆被瞬间清零**。
2. **`exp(g)` 的下溢**：FP16 最小正规数 `2^-14 = 6.1e-5`，对应 `g < −9.70` 即下溢为 0（次正规可到 `2^-24`，但精度崩塌）。而初始化 `A_h ∈ [0.01,16]`、`softplus(1) = 1.3133`，故初始 `g ∈ [−21.0, −0.013]` —— **有相当一部分 head 的 `g` 落在 `−21`，FP16 下必然清零**。这是"初始就坏"级别的错误，不是边角。
3. **`exp` 的尾数**：BF16 只有 8 bit 尾数，`exp(g)` 相对误差 `≈ 2^-8 = 0.39%`。递推 1000 步后累积为 `(1.0039)^1000 ≈ 49×` 的偏差。FP32 的 `2^-24` 累积后为 `1.0000006^1000 ≈ 1.0006`，可忽略。

**坑**：
- `mamba_ssm_dtype=float32` 这个 config 键在本文件里**没有被引用**（全文件 grep 无命中）。真正的 FP32 来自 `:325` / `:445` 的显式 `.to(torch.float32)`。若你自研 kernel 声称"遵循 config"，实际并不存在这条通路，别把 config 当规范。
- `dt_bias` 初始化为 **1**（`:519`、`:924` `init.ones_`），不是 0；`A_log` 初始化为 `log(U(0.01,16))`（`:522`、`:928`）。这两个初值决定"初始记忆窗口 ≈ 1/(A·Δ) 步"，从 0.06 步到 76 步跨三个数量级。**FP16 存储 `A_log` 时，`log(0.01) = −4.6` 与 `log(16) = 2.77` 都能表示，问题不在 `A_log` 而在 `g`**。
- fla 的分块 kernel 会把 `g` 再乘 `1/ln2`（`chunk.py:57` `scale=RCP_LN2`）转成 **log2 域**，之后全程用 `exp2`（`chunk_delta_h.py:250`、`chunk_o.py:138`）。这是纯数学等价变换（`e^x = 2^{x·log2 e}`），但**硬件上意味着你要一条 `exp2` 指令，而不是 `exp`**——`exp2` 的实现便宜得多（指数位直接搬），值得在 ISA 里专门留一条。
- `g` 是**标量 per head**，但在 kernel 里会做 `decay[..., None, None]` 广播到 `128×128` 状态（`:469`）。硬件若按向量实现会浪费 128× 的乘法器；正确做法是把 `e^{g}` 存进一个标量寄存器，用它做状态的标量乘。

---

### A⑨ `torch_recurrent_gated_delta_rule`：decode 单 token 递推

**作用**：decode 时的真·串行核心。每来一个 token，对 16 个 head 各自的 `128×128` 状态做"衰减 → 读出预测 → 残差修正 → 写回 → 再读出"。
**调用**：`torch_recurrent_gated_delta_rule(query, key, value, g=g, beta=beta, initial_state=recurrent_state, output_final_state=..., use_qk_l2norm_in_kernel=True)`（`:613–624`）。Triton 版为 `fused_recurrent_gated_delta_rule_fwd_kernel`（fla `fused_recurrent.py:30`）。
**公式**（对每个 head `h`，`S ∈ R^{128×128}` 为 k_dim×v_dim 状态）：

```
① 衰减：      S ← S · e^{g_h}                                  标量乘，逐元素 16,384 次
② 读预测：    m = Sᵀ k̂ ,     m_j = Σ_{i=1..128} S[i,j] · k̂_i        m ∈ R^128
③ 残差：      u = β_h · (v − m)                                 u ∈ R^128
④ 写入：      S ← S + k̂ ⊗ u ,   S[i,j] += k̂_i · u_j              秩-1 更新
⑤ 读出：      o = Sᵀ q̂ ,     o_j = Σ_{i=1..128} S[i,j] · q̂_i        o ∈ R^128
```

其中 `k̂ = k̃/√128`、`q̂ = q̃/√128`，`q̃,k̃` 为 A⑥ 的 L2 归一结果。**注意 ⑤ 用的是更新后的 `S`**，所以当前 token 的输出包含自身写入贡献——这与 chunk 形式里保留对角项的 `triu(1)` 掩码（`:356`）一致。

**"delta" 的含义**：`v − Sᵀk` 是"当前状态对该 token 的预测残差"。普通线性注意力直接写 `v`，会把已记住的信息重复写一遍；delta rule 只写**修正量**，等价于对状态做一次在线梯度下降（目标 `‖Sᵀk − v‖²`，学习率 β）。这是 DeltaNet 系列记忆容量显著高于普通线性注意力的原因。

**形状**：
- 输入：`q,k:[B,1,16,128]`，`v:[B,1,16,128]`，`g,β:[B,1,16]`，`initial_state:[B,16,128,128]`
- 输出：`core_attn_out:[B,1,16,128]`，`final_state:[B,16,128,128]`

**MAC 推导**（每 head）：

```
① 衰减      ：128×128 = 16,384 次乘法（无加法，不计 MAC）
② Sᵀk       ：128×128 = 16,384 MAC
③ 残差      ：128 次乘 + 128 次减 = 128 MAC
④ 外积写回  ：128×128 = 16,384 MAC
⑤ Sᵀq       ：128×128 = 16,384 MAC
────────────────────────────────────────────
每 head MAC = 16,384 × 3 + 128 = 49,280（若把②④⑤的初始化也算进则为 49,152）
每层 MAC    = 16 × 49,152 = 786,432 = 0.786 M
含衰减的总运算量 = 16 × (49,152 + 16,384) = 1,048,576 ops
```

**状态带宽**（decode 的真正瓶颈）：

```
状态元素 = 16 × 128 × 128 = 262,144 个 FP32 = 1,048,576 B = 1 MiB / 层
每 token 读 + 写 = 2 MiB / 层  →  18 层 = 36 MiB/token
（若状态用 BF16 存，减半到 18 MiB/token，但见下方精度论证：不可行）
```

**精度**：**状态必须 FP32**，四重论据：
1. **累加链无界**：`S` 的每个元素在序列维度上被反复"衰减+累加"，有效步数 `≈ 1/(1−e^{g})`。`g = −0.01` 时约 100 步，`g = −0.001` 时约 1000 步。BF16 累加 1000 次 → 误差 `sqrt(1000)·2^-8 ≈ 12%`。
2. **残差是病态减法**：`v − Sᵀk` 在模型收敛后 `‖Sᵀk‖ ≈ ‖v‖`（状态已记住该 key），差值可能只有 `v` 的 1%。此时 `Sᵀk` 的绝对误差被**放大 100 倍**才成为 `u` 的相对误差。BF16 的 `2^-8` 放大成 `2^-8 × 100 ≈ 39%`。这是 delta rule 必须高精度的**结构性**原因，不是调参能解决的。
3. **读出的二次放大**：⑤ 又对 `S` 做一次 128 项归约，与 ② 同量级。
4. **配置与源码双重要求**：`mamba_ssm_dtype=float32`；源码 `:445` 把 `q,k,v,β,g` 全部 `.to(torch.float32)`，状态由 `torch.zeros(..., dtype=value.dtype)`（`:460`）创建——`value` 此时已是 FP32，所以状态天然 FP32，输出才 `.to(initial_dtype)`（`:482`）。

**Kernel 内部（fla `fused_recurrent.py:104–161`，已核实）**：

```
b_h   = zeros([BK, BV], fp32)          # 状态在寄存器里，全程 FP32
b_h  += initial_state
b_k   = k / sqrt(sum(k²) + 1e-6)       # L2 归一在 kernel 内（:120）
b_h  *= exp(b_g)                       # 衰减（:138）
b_v   = b_beta * (b_v − sum(b_h * b_k[None,:], 1))    # :155
b_h  += b_v[:,None] * b_k[None,:]                     # :159
b_o   = sum(b_h * b_q[None,:], 1)                     # :161
```

状态**不落 HBM**，整个 token 的 5 步在寄存器/SMEM 内完成，只在 token 边界写回 `[128,128]`——这正是你自研单元应当模仿的形态。

**坑**：
- **这是 decode 唯一不可并行的路径**：token `t` 的 `S` 依赖 `t−1`。16 个 head 之间可并行（16 路），head 内 128×128 的 4 个 pass 也可流水，但**跨 token 的依赖链长度 = S**。
- **临界路径估算**：若 VPU 有 128 lane，单 pass `128×128 = 16,384 MAC` 需 128 cycle；4 个依赖 pass（衰减 ‖ ② → ③ → ④ → ⑤；②③④⑤ 严格串行，衰减可与 ② 并行）≈ 3×128 = 384 cycle + 2 次 `rsqrt`/`exp`。16 head 若只有 1 组 MAC 阵列则 ×16 = 6,144 cycle/token/层；若 16 head 全并行则 384 cycle/token/层。**这个数字直接决定 decode 的 tok/s 上限**（18 层 × 384 = 6,912 cycle/token）。
- **`output_final_state=cache_params is not None`**（`:620`）：无 cache 时不返回状态，省一次 1 MiB 写；有 cache 时必须回写。你的硬件若默认总是回写，decode 会白付 1 MiB/token/层。
- **`initial_state` 的 dtype 转换**：`:462` `initial_state.to(value)`，若 cache 里存的是 BF16 而 `value` 是 FP32，每 token 都要做一次 1 MiB 的 dtype 转换。**cache 必须直接以 FP32 分配**。
- **`g` 的符号约束**：文档（`:305–306`）明确 "entries must be <= 0"。若硬件/量化让 `g` 出现 `+1e-6`，`exp(g) > 1` 会让状态**指数发散**（1000 步后 `e^{0.001·1000} = 2.7×`，再乘 18 层）。测试必须包含 `g ≡ 0`（无衰减）与 `g` 极负（清零）两个端点。
- **`cu_seqlens` 分支**（`:622`）：变长 batch 时传入，kernel 走 varlen 路径。batch 内多序列的状态是**独立**的，别把 padding 段的状态当成"上一序列的延续"。

---

### A⑩ `torch_chunk_gated_delta_rule`：prefill 分块算法

**作用**：把 `O(S)` 的串行递推改写成 `O(S/64)` 的块间串行 + 块内全并行，使 prefill 能吃满矩阵引擎。
**调用**：`torch_chunk_gated_delta_rule(q, k, v, g=g, beta=beta, chunk_size=64, initial_state=..., use_qk_l2norm_in_kernel=True)`（`:626–637`）。Triton 版调用链（fla，已核实）：

```
chunk_gated_delta_rule_fwd                     chunk.py:33
 ├─ chunk_local_cumsum(g, scale=1/ln2)         # log2 域前缀和
 ├─ chunk_gated_delta_rule_fwd_intra           chunk_fwd.py:332
 │   ├─ (BT=64) chunk_gated_delta_rule_fwd_kkt_solve_kernel   # KKᵀ + solve_tril 融合, BC=16
 │   └─ recompute_w_u_fwd                      wy_fast.py:255   # w = A·(β k e^g), u = A·(β v)
 ├─ chunk_gated_delta_rule_fwd_h               chunk_delta_h.py:59   # 块间状态扫描
 └─ chunk_fwd_o                                chunk_o.py:58        # o = q·h + (q kᵀ ⊙ decay)·v_new
```

**分块算法（每 head，块大小 `BT = 64`，`K = V = 128`）**：

设块内位置 `i, j ∈ [1, BT]`，`g_i` 为块内累积衰减（log 域），`β_i` 为写入门。

```
【第 0 步】块内累积衰减（log 域前缀和，加法而非乘法）
    G_i = Σ_{t=1..i} g_t

【第 1 步】构造严格下三角的"块内 delta 系统"
    A_ij = β_i · (k_i · k_j) · e^{G_i − G_j} ,   j < i
    A_ij = 0                                    ,   j ≥ i     （严格下三角）
    ut_system = A                       （transformers 里叫 ut_system，:370）

【第 2 步】WY 表示：解 (I + A) 的逆
    T = (I + A)^{−1}                    （单位下三角，前代法可解）
    u = T · (β ⊙ v)                     u ∈ R^{BT×V}   "u is actually the new v"（fla 注释）
    w = T · (β ⊙ k ⊙ e^{G})             w ∈ R^{BT×K}

【第 3 步】块间状态扫描（对块索引 c = 1..S/BT 串行）
    v_new[c] = u[c] − w[c] · S[c]                    # 扣掉旧状态已能预测的部分
    o[c]     = q̃[c]·e^{G} · S[c]  +  (M ⊙ q̃ kᵀ)[c] · v_new[c]
               └── 块间读出 ──┘       └────── 块内读出 ──────┘
      M_ij = e^{G_i − G_j}  (j ≤ i)，否则 0            # 块内因果掩码，对角保留
    S[c+1]   = e^{G_BT} · S[c] + k̃ᵀ · v_new[c]
               └ 整块衰减 ┘      └ 秩-BT 更新 ┘
      k̃_i = k_i · e^{G_BT − G_i}                      # 块内各 token 衰减到块尾

【第 4 步】输出 reshape 回 [B,S,16,128]，丢弃 padding
```

**为什么"先减后写"是 delta 而非普通线性注意力**：第 3 步的 `v_new = u − w·S` 中，`w·S` 正是"旧状态对本块各 token 的预测"，减掉它后只把**残差**写进状态。这与 A⑨ 的 `u = β(v − Sᵀk)` 是同一件事，只是把 64 个 token 的残差用一次矩阵乘一次算完。

**`(I + A)^{−1}` 的求解（BC=16 子块，已核实 fla `chunk_fwd.py:120–294`）**：

```
BT = 64 切成 4 个 BC = 16 的子块，共 10 个下三角 [16,16] 块（4 对角 + 6 非对角）
  Step A：对角块前代求逆   (I + A_dd)^{−1}         4 × 16²/2·16 ≈ 4×2,048 = 8,192 MAC
  Step B：16→32 块合并      2 × 2×16³              = 8,192 MAC
  Step C：32→64 块合并      2×32³                   = 65,536 MAC
  合计 ≈ 81,920 MAC/head/chunk
```

transformers 的 fallback 用的是**另一条路**：`torch.linalg.solve_triangular(ut_system, v_beta, upper=False, unitriangular=True)`（`:381–382`），直接对 `[64,128]` 右端项做前代，代价 `BT²/2 × V = 2,016×128 = 258,048 MAC × 2`（u 和 w 各一次）。导出模式下走显式循环（`:383–390`）。

这里有个值得抄进硬件设计的取舍。fla 的 Triton 版故意多花算力（先显式求逆 82 K，再做两次 `64×64×128` GEMM，共 1.13 M），换来全 GEMM、没有三角求解；torch 版只花 516 K，但含两次三角前代，步长是数据依赖的串行。在矩阵引擎上，多 0.6 M MAC 换掉三角求解几乎一定划算。

**形状**：`q,k:[B,S,16,128]`，`v:[B,S,16,128]`，`g,β:[B,S,16]`，`S:[B,16,128,128]` → `o:[B,S,16,128]`，`final_state:[B,16,128,128]`

**MAC 推导**（每 head 每 chunk，`BT=64, K=V=128`）：

| 步骤 | 算式 | MAC |
|---|---|---:|
| ① `A = β·KKᵀ` | 64 × 64 × 128 | 524,288 |
| ② `(I+A)^{−1}`（BC=16） | 见上 | 81,920 |
| ③ `u = T·(β⊙v)` | 64 × 64 × 128 | 524,288 |
| ④ `w = T·(β⊙k⊙e^G)` | 64 × 64 × 128 | 524,288 |
| ⑤ `v_new = u − w·S` | 64 × 128 × 128 | 1,048,576 |
| ⑥ `o_inter = q̃e^G·S` | 64 × 128 × 128 | 1,048,576 |
| ⑦ `q̃kᵀ`（块内注意力） | 64 × 64 × 128 | 524,288 |
| ⑧ `o_intra = (M⊙q̃kᵀ)·v_new` | 64 × 64 × 128 | 524,288 |
| ⑨ `S += k̃ᵀ·v_new` | 128 × 64 × 128 | 1,048,576 |
| **每 chunk 每 head** | | **5,849,088** |

```
每 token 每 head = 5,849,088 / 64 = 91,392
每 token 每层   = 91,392 × 16 = 1,462,272 MAC = 1.46 M
```

对比 decode 的 `0.786 M`：**prefill 的 delta 部分贵 `1.86×`**（因为块内要重算 `q kᵀ`、`k kᵀ` 和显式求逆）。但换来了串行深度从 `S` 降到 `S/64`。

**精度**：
- `cumsum(g)` 在 **log2 域**做，FP32；64 项累加误差 `sqrt(64)·2^-24 ≈ 5e-7`，安全。
- `e^{G_i − G_j}`：`i − j` 最大 63，`g ≈ −0.01` 时指数范围 `[−0.63, 0]`；`g ≈ −16` 时 `[−1008, 0]`，`e^{−1008}` 在 FP32 下**下溢为 0**（FP32 最小正规数 `1.2e-38` 对应 `−87.3`）。这是**期望行为**（有效遗忘），但要确保下溢产生 `+0` 而不是 `NaN`（`0 × inf`）。fla 的 kernel 专门为此加了边界掩码注释（`chunk_fwd.py:177–180`："boundary mask to prevent NaN from 0 * inf (IEEE 754)"）——**这是一个真实踩过的坑，你的 RTL 也要防**。
- `(I+A)^{−1}`：`A` 的元素 `|A_ij| ≤ β_i·|k_i·k_j|·1 ≤ 1`（L2 归一后 `|k_i·k_j| ≤ 1`）。但 `T = I − A + A² − ...`，若 key 高度重复（`k_i·k_j ≈ 1`），`T` 的元素可放大到 `C(63,31) ≈ 9e17` 量级（理论上界）。实际模型里 `T` 的 `max|·|` 通常在 `1e2 ~ 1e4`。**前代必须在 FP32**，且建议加一个 `max|T|` 的运行时哨兵（超过阈值就退回 FP32 精算）。
- 状态累加同 A⑨，FP32。

**坑**：
- **`chunk_size` 只能是 16/32/64**（`chunk_fwd.py:369` 显式 `raise`）。`BT=64` 走融合 kernel（`BC=16`），其它值走非融合两段式（`chunk_scaled_dot_kkt_fwd` + `solve_tril`）。
- **短序列惩罚**：`pad_size = (64 − S%64) % 64`（`:337`）。`S = 1` 也要跑满一个 64 的块，prefill 首 token 的延迟 ≈ 满块。测试要覆盖 `S = 1, 63, 64, 65`。
- **`h` 中间缓冲的 DRAM 占用（prefill 的真实瓶颈）**：fla 把每个 chunk 的状态都落盘 `h:[B, NT, H, 128, 128]`（`chunk_delta_h.py:185` 逐块 `tl.store`）。`S = 8192` 时 `NT = 128`：

```
128 chunk × 16 head × 128 × 128 = 33,554,432 元素
FP16/BF16 存储 = 67.1 MB / 层   （FP32 则 134 MB）
写一次 + 读一次 = 134 MB 流量 / 层 / 8K-token
```

这是**单层峰值**，18 层不能同时驻留，但 67 MB 远超任何片上 SRAM，必然走 HBM。若你的引擎支持"块状态驻留 + 融合 `chunk_fwd_o`"，可省掉这 134 MB。
- **`solve_triangular` 的可移植性**：`is_torchdynamo_exporting()` 会切到 `:383–390` 的 64 步 Python 循环。**导出图里会出现 64 个 `[i,i]` 的动态切片**，编译期爆炸。自研后端应直接实现 Step A/B/C 的三段块求逆。
- **`q` 的缩放位置**：`:333–334` 在 L2 之后乘 `d^-0.5`，而 `:399` 又乘 `e^{G}`。两个标量可合并成 `q̃ · d^{-0.5} · e^{G}`，硬件上应把 `d^{-0.5}` 折进 `e^{G}` 的查表（`exp(G − 0.5·ln d)`），省一次 2048 元素的乘。注意 `ln(128)/2 = 2.4245`。
- **块内掩码保留对角**：`strictly_upper_mask = triu(1)`（`:356`），即对角被保留，`M_ii = 1`。若误用 `triu(0)`，当前 token 看不到自己的写入，输出全错但不报错。

---

### A⑪ `Qwen3_5RMSNormGated`：按 head 的 value 维归一 + `silu(z)` 门控

**作用**：对每个 head 的 128 维输出做 RMSNorm（稳定 delta 输出的尺度），再乘 `silu(z)` 做逐元素门控（让模型能"关掉"某些通道）。
**调用**：`self.norm(core_attn_out, z)`（`:646`），`Qwen3_5RMSNormGated(head_v_dim=128, eps=1e-6)`（`:525`），hub kernel 名 `RMSNormGated`。
**公式**：

```
输入先展平：x ∈ R^{2048} = [B·S·16, 128]，z 同形
rstd_j = 1 / sqrt( (1/128)·Σ_{i=1..128} x_{h,i}² + 1e-6 )
y_{h,j} = x_{h,j} · rstd_j · w_j · silu(z_{h,j})
silu(z) = z · σ(z)
```

**关键差异**：归约域是 **`head_v_dim = 128`**，不是 2048；权重 `w` 长度 **128**（16 个头共享同一组 128 个参数，`:525` 传的是 `head_v_dim`）。

**形状**：`x:[B,S,16,128] → reshape [B·S·16, 128]`，`z` 同 → 输出 `[B,S,16,128] → [B,S,2048]`
**MAC 推导**：

```
平方和   ：2048 次（16 头 × 128）
归一化   ：2048 次乘（rstd）+ 2048 次乘（w）  = 4,096
门控     ：2048 次 silu（≈1 exp + 1 div + 2 mul）+ 2048 次乘 = 2,048 MAC + 2,048 超越
合计 ≈ 8,192 MAC = 0.008 M                                （占单层 0.014%）
```

**精度**：
- 平方和 **FP32**（128 项，BF16 下 4.4% 误差；源码 `:213` `hidden_states.to(torch.float32)`）。
- **注意源码的精度陷阱**（`:217`）：`self.weight * hidden_states.to(input_dtype)` —— 归一化后的值**先被 round 回 BF16**，再与 FP32 的 `w` 相乘（自动提升回 FP32），最后乘 `silu(gate.float())` 再 `.to(input_dtype)`。也就是说**归一化结果经历过一次 BF16 截断**。自研硬件若全程 FP32 反而**比参考实现更准**，会导致 bit-exact 对不上——测试基准要按"容差比对"而非"逐位比对"。
- `silu(z)` 用 **FP32**：`z` 是 `in_proj_z` 的输出，动态范围可达 `±20`；BF16 无溢出问题但尾数只有 8 bit，`silu` 在 `z ≈ 0` 附近近似线性，8 bit 尾数 → 0.4% 门控误差，直接乘在输出上。

**坑**：
- **归约域必须是 128（head 内），不能是 2048**。`:644` 的 `reshape(-1, head_v_dim)` 就是为了这个。若在 2048 上归一，等价于把所有头混在一起，输出尺度全错。
- **`z` 与 `core_attn_out` 的展平顺序必须一致**：`core_attn_out` 来自 kernel 的 `[B,S,16,128]`，`z` 来自 `in_proj_z` 的 `[B,S,2048]→[B,S,16,128]`。两者的 `h` 维语义必须相同（第 `h` 个头对第 `h` 个门）。这条错了**不会报 shape 错**（都是 `[·,128]`），只会掉点——是最难查的一类 bug。
- `w` 只有 128 个参数（512 B），可常驻寄存器；**不要**按 2048 分配。
- 这个算子是"per-head reduction + 逐元素门控"的复合体，**无法拆成矩阵乘**，必须整体放 VPU。

---

### A⑫ `out_proj`

**作用**：把 16 个 head 的 128 维输出（共 2048）投回残差流宽度 2048。
**调用**：`self.out_proj(core_attn_out)`，`nn.Linear(2048, 2048, bias=False)`（`:526`）。
**公式**：

```
y_o = Σ_{i=1..2048} W_out[o,i] · c_i ,    c = concat_h(y_h) ∈ R^{2048}
```

**形状**：`[B,S,2048] → [B,S,2048]`
**MAC 推导**：

```
1 × 2048 × 2048 = 4,194,304 MAC = 4.19 M                  （占单层 7.0%）
```

**精度**：MXFP8 权重 + MXFP8 激活，**FP32 累加**。输出直通残差，误差会**永久留在残差流里**（残差流是 24 层的公共总线，误差只增不减）。建议此处输出用 **FP16 而非 BF16**（多 3 bit 尾数），或直接以 FP32 进残差加法器再统一 round（见 A⑬）。
**坑**：
- 输入是 `[B,S,16,128]` reshape 成的 `[B,S,2048]`（`:647`），**顺序是 head-major**。`out_proj` 的权重列排布必须与这个顺序一致；若你的编译器把 `[16,128]` 转成 `[128,16]`（head-minor）以提高 L2 归一效率，`out_proj` 前必须转回，或离线转置权重列。
- 与 A② 对称：`in_proj_qkv` 是 `2048→6144`（3× 扩张），`out_proj` 是 `2048→2048`（1×）。整层的"投影开销"是 `12.58 + 4.19 + 4.19 = 20.96 M`，占 35%。

---

### A⑬ 残差加

**作用**：`hidden = residual + attn_out`，把 DeltaNet 的输出汇入残差流。这是 `Qwen3_5DecoderLayer.forward` 里的 `hidden_states = residual + hidden_states`（`:893`），不在 `Qwen3_5GatedDeltaNet` 内部。
**调用**：Python 原生 `+`（`:893`），无专用 kernel。
**公式**：

```
h_out[i] = h_in[i] + y_out[i] ,   i = 1..2048
```

**形状**：`[B,S,2048] + [B,S,2048] → [B,S,2048]`
**MAC 推导**：`2048` 次加 = **2,048 MAC 当量**（0.003%，纯记帐用）。
**精度**：
- **加法本身必须 FP32 或 FP16**：BF16 的 `2^-8` 相对误差加在残差流上，24 层累积（每层 2 次残差加，共 48 次）→ `sqrt(48)·2^-8 ≈ 2.7%`。**这是 BF16 残差流的已知问题**，主流做法是残差流用 FP32 保存、只在每个 GEMM 输入处 round 到 BF16（"FP32 residual stream"）。
- 若你的引擎只有 BF16 残差，建议至少**每 4 层做一次 FP32 重归一**，或改用 stochastic rounding（需模型侧验证，非标准做法）。

**坑**：
- 残差加的**位置**：`:870` 存 `residual`，`:872` 归一，`:876` 走 mixer，`:893` 加回。**归一化在残差分支之外**（pre-norm），这是 Qwen 系列的标准结构；若误做成 post-norm，数值完全不可用。
- 同一层里还有**第二次**残差加（`:896–899`，MLP 之后），两次加的输入 dtype 必须一致。
- 该算子是"读 2 份、写 1 份 2048 元素"的纯带宽操作，`12 KB/token/层`，应尽量与 A⑫ 的 GEMM 输出**融合**（GEMM 累加器直接加残差再落盘），否则每层多一次 4 KB 往返。

---

### A⑭ SwiGLU MLP（与层型 B 共用，详见 6.3）

**作用**：逐 token 的 2 层前馈，`2048 → 6144 → 2048`，带 SwiGLU 门控。
**调用**：`self.mlp(hidden_states)`（`:898`），`Qwen3_5MLP`。
**MAC 推导**：

```
gate_proj : 2048 × 6144 = 12,582,912
up_proj   : 2048 × 6144 = 12,582,912
down_proj : 6144 × 2048 = 12,582,912
────────────────────────────────────────
合计 = 37,748,736 MAC = 37.75 M                            （占单层 63.3%）
```

**精度**：三个 GEMM 均可 MXFP8 + FP32 累加；`silu(gate)` 在 FP32。详见 6.3。

---

### 6.1.14 层型 A 单 token 合计（decode）

| # | 算子 | MAC | 占比 | 类型 | 归属 |
|---|---|---:|---:|---|---|
| A① | `input_layernorm` | 4,096 | 0.007% | 归约(2048) | VPU |
| A② | `in_proj_qkv` | 12,582,912 | 21.10% | GEMV/GEMM | **MXFP8 引擎** |
| A③ | `in_proj_z` | 4,194,304 | 7.03% | GEMV | **MXFP8 引擎** |
| A③ | `in_proj_b` | 32,768 | 0.055% | GEMV (N=16) | VPU / 合并 |
| A③ | `in_proj_a` | 32,768 | 0.055% | GEMV (N=16) | VPU / 合并 |
| A④ | `causal_conv1d`+SiLU | 24,576 | 0.041% | 深度卷积(跨 token) | VPU |
| A⑤ | `split`+`reshape` | 0 | 0 | 视图 | — |
| A⑥ | L2 norm (q,k) | 8,192 | 0.014% | 归约(128) | VPU |
| A⑦ | `β = sigmoid(b)` | 16 | ~0 | 逐元素(16) | VPU |
| A⑧ | `g = −e^{A_log}·softplus` | 48 | ~0 | 超越(16) | VPU (FP32) |
| A⑨ | **delta 递推** | **786,432** | **1.32%** | **串行递推** | VPU / 状态单元 |
| A⑪ | `RMSNormGated` | 8,192 | 0.014% | 归约(128)+门控 | VPU |
| A⑫ | `out_proj` | 4,194,304 | 7.03% | GEMV/GEMM | **MXFP8 引擎** |
| A⑬ | 残差加 | 2,048 | 0.003% | 逐元素 | VPU |
| — | `post_attention_layernorm` | 4,096 | 0.007% | 归约(2048) | VPU |
| A⑭ | MLP (SwiGLU) | 37,748,736 | 63.32% | GEMM | **MXFP8 引擎** |
| | **合计** | **59,623,488** | **100%** | | |

```
≈ 59.62 M MAC / token / 层     （若不计两个 2048 维 RMSNorm 的 4 K，则 59.61 M）
= 119.2 MFLOP / token / 层
18 层 DeltaNet 合计：59.62 × 18 = 1,073.2 M MAC = 1.073 GMAC = 2.15 GFLOP / token
```

**prefill 版本**：把 A⑨ 的 `786,432` 换成 A⑩ 的 `1,462,272`：

```
59,623,488 − 786,432 + 1,462,272 = 60,299,328 ≈ 60.30 M MAC/token
即 prefill 比 decode 贵 1.13%（增量全在 delta 部分：+0.676 M）
```

**权重字节（decode 的带宽下限）**：

```
每层参数 = 12,582,912(qkv) + 4,194,304(z) + 32,768(b) + 32,768(a) + 24,576(conv)
         + 4,194,304(out) + 37,748,736(MLP) + 2,048(ln) + 2,048(post-ln)
         + 128(norm-gated w) + 32(A_log, dt_bias)
       = 58,814,624 ≈ 58.81 M 参数/层
MXFP8 (1 B/参数) → 58.81 MB/token/层
算术强度 = 59.62 M MAC / 58.81 MB = 1.014 MAC/B = 2.03 FLOP/B
```

这就是 roofline 上最不利的情形：decode 极端带宽受限（AI ≈ 1 MAC/B）。假如引擎峰值 100 TOPS(MXFP8)，喂满它需要 `100e12/2 / 1.014 ≈ 49 TB/s` 的权重带宽，现实中做不到。所以 decode 的性能上限完全由权重带宽除以 58.81 MB 决定；只看 18 层 DeltaNet 的话，理论 tok/s ≤ `BW / 1.058 GB`。

**状态带宽（decode 的第二个下限）**：

```
循环状态：16 × 128 × 128 × 4 B = 1 MiB / 层，读 + 写 = 2 MiB / token / 层
conv 状态：6144 × 3 × 2 B = 36 KiB / 层，读 + 写 = 72 KiB / token / 层
18 层合计：2.06 MiB × 18 ≈ 37.1 MiB / token   （与 1.058 GB 的权重流相比可忽略）
```

---

### 6.1.15 硬件归属：哪些能上 MXFP8 矩阵引擎，哪些不能

| 算子 | 能否上矩阵引擎 | 原因 |
|---|---|---|
| A② `in_proj_qkv` | 最佳目标 | 纯 GEMM `1×2048×6144`，K=2048 足够深，FP32 累加 |
| A③ `in_proj_z` | 可以 | `1×2048×2048`，与 A② 同构 |
| A③ `in_proj_b/a` | 不划算 | `N=16`，列利用率 12.5%（128 宽 tile）；MAC 仅 0.066 M，启动加权重搬运才是成本 |
| A④ `causal_conv1d` | 不能 | ① `groups=6144` 的深度卷积，等效矩阵是 6144 个独立的 `1×4` 行，不是 GEMM；② 跨 token 依赖 conv_state，必须串行滑窗；③ SiLU 是超越函数 |
| A⑥ L2 norm | 不能 | 128 维归约加 `rsqrt`，无矩阵乘结构 |
| A⑦ `sigmoid` | 不能 | 16 维逐元素超越 |
| A⑧ `g` 计算 | 不能 | 16 维 `exp`/`softplus`，且必须 FP32 |
| A⑨ delta 递推 | 不能 | ① 跨 token 串行（`S_t` 依赖 `S_{t−1}`）；② 结构是 "GEMV + 秩-1 外积 + GEMV"，三个 pass 严格串行；③ 累加器就是 `128×128` 状态本身，必须常驻，与 GEMM 的流式累加模型冲突；④ 必须 FP32，MXFP8 的 8 bit 尾数过不了残差减法这一关 |
| A⑩ prefill 分块 | 部分可以 | ①③④⑤⑥⑦⑧⑨ 全是规整 GEMM（`BT×BT×K`、`BT×K×V`），可上引擎；② `(I+A)^{−1}` 的前代求解是数据依赖的串行三角求解，不能上，需 VPU 或专用小矩阵求逆单元（fla 的 BC=16 三段块求逆正好适配 `16×16` 的小阵列） |
| A⑪ `RMSNormGated` | 不能 | 128 维归约加 `silu` 门控 |
| A⑫ `out_proj` | 可以 | `1×2048×2048` 纯 GEMM |
| A⑬ 残差加 | 不能 | 逐元素加，建议融进 A⑫ 的累加器输出级（GEMM epilogue） |
| A⑭ MLP | 最大目标 | 3 个 `2048×6144` GEMM，占 63% MAC |

三类判据：
1. 串行依赖跨 token 的不能上引擎：A④（conv_state）、A⑨（recurrent state）、A⑩②（三角求解，跨行依赖）。
2. 归约加超越函数的不能上引擎：A⑥、A⑦、A⑧、A⑪、A①（RMSNorm）、A⑬。
3. `N` 太小（< 64）的能上但不划算：A③ 的 `b/a`（`N=16`）。

**MAC 视角的结论**（这是给架构师的核心数字）：

```
可上矩阵引擎的 MAC = A②+A③+A⑫+A⑭ = 12,582,912 + 4,259,840 + 4,194,304 + 37,748,736
                  = 58,785,792  →  占 98.60%
只能上 VPU 的 MAC  = 837,696      →  占 1.40%（其中 delta 递推 786,432 占 1.32%）
```

**但延迟视角完全相反**：decode 时 `1.32%` 的 delta 递推贡献了**几乎 100% 的串行关键路径**（16 head × 4 个依赖 pass，`≥384 cycle`），而 98.6% 的 GEMM 是完全可流水/可并行的。**这就是为什么 MXFP8 引擎再快也提不高 decode tok/s——瓶颈在 VPU 的递推单元和权重带宽，不在矩阵引擎。**

---

### 6.1.16 精度预算表（按算子）

| 算子 | 输入存储 | 计算/累加 | 输出存储 | 强制 FP32 的理由（数量级） |
|---|---|---|---|---|
| A① RMSNorm | BF16 | **FP32** | BF16 | 2048 项平方和：BF16 误差 `2^-8·√2048 ≈ 0.18`（18%）→ rstd 偏 9% |
| A② qkv | MXFP8 | **FP32** | BF16 | K=2048 累加：FP16 累加 `2^-11·√2048 ≈ 2.2%`；FP32 `2.7e-6` |
| A③ z | MXFP8/FP16 | FP32 | BF16 | 门控乘在输出上，`2^-8` 直接可见 |
| A③ b | **FP16**（禁 FP8） | FP32 | FP32 | `Δβ = β(1−β)Δb ≤ 0.25Δb`；β 误差在 1000 步递推后放大到 `1−0.99^1000 ≈ 1` |
| A③ a | **FP16**（禁 FP8） | FP32 | FP32 | 进 `softplus→exp`，工作点在 `σ(1)=0.73`，无饱和保护 |
| A④ conv | BF16 | FP16 可 | BF16 | 4 tap，误差 `√4·2^-11 ≈ 0.1%`；SiLU 在 `u<−11` 时 FP16 溢出（BF16 安全） |
| A⑥ L2 norm | FP32 | **FP32** | FP32 | 128 项平方和：BF16 `2^-8·√128 ≈ 4.4%`；FP32 `6.7e-7` |
| A⑦ β | FP32 | **FP32** | FP32 | 同上（b 已 FP32，sigmoid 无额外损失） |
| A⑧ g | FP32 | **FP32** | FP32 | `exp(g)`：`g=−21` 时 FP16 下溢为 0；BF16 尾数 `2^-8` → 1000 步后 49× 漂移 |
| A⑨ 状态 | **FP32** | **FP32** | **FP32** | ① 1000 步累加：BF16 `√1000·2^-8 ≈ 12%`；② 残差 `v−Sᵀk` 病态减法放大 100×；③ `mamba_ssm_dtype=float32` |
| A⑩ 分块 | FP32 中间量 | **FP32**（`A`、`T`、`cumsum`） | FP32 状态 / BF16 输出 | `T=(I+A)^{-1}` 元素可达 `1e4`，前代在 FP16 下丢 6 bit |
| A⑪ RMSNormGated | FP32 | **FP32** | BF16 | 128 项归约同 A⑥；`silu(z)` 的 `z∈±20` |
| A⑫ out_proj | MXFP8 | **FP32** | **FP16 建议** | 误差直通残差流，48 次残差加累积 `√48·2^-8 ≈ 2.7%` |
| A⑬ 残差 | FP32 残差流 | **FP32** | BF16（GEMM 前） | 同上；建议学 "FP32 residual stream" |
| A⑭ MLP | MXFP8 | **FP32** | BF16 | K=2048 / 6144，同 A② |

**三条硬边界**：
1. **循环状态 `S` 与衰减 `g` 永不进 FP8/FP16** —— 这是 1.32% 的 MAC，但决定 100% 的精度。
2. **所有 K ≥ 512 的 GEMM 必须 FP32 累加** —— FP16 累加在 K=2048 时误差 2.2%，K=6144 时 3.8%。
3. **`(I+A)^{−1}` 的前代必须 FP32** —— 这是唯一一个"条件数可能爆"的算子，需要有运行时哨兵。

---

### 6.1.17 实现/测试坑清单（按优先级）

| # | 坑 | 后果 | 对策 |
|---|---|---|---|
| 1 | **`conv1d` 是 channels-first**，但 `in_proj_qkv` 输出是 `[B,S,6144]` | 需两次 `transpose`，其中一次会触发 `contiguous()` 拷贝（12 KB/token） | DMA 直接按 `[B,6144,S]` 落盘；或用 channels-last 版 conv 微码 |
| 2 | **`conv1d.bias` 是 `None`**（`bias=False`） | 若 RTL 里留了 bias 加法，白加 6144 次 | 编译期常量折叠掉 |
| 3 | **`conv_state` 必须先 `copy_` 再 conv**（`:246–248`） | 乱序 → 投机解码路径读到被覆盖窗口，静默错 | 硬件上用双缓冲（ping-pong 3 帧） |
| 4 | **padding token 未掩** | conv_state 被污染，后续所有 token 偏移（mamba #66） | 层入口 `apply_mask_to_padding_states`（`:543`）必须在 conv 之前 |
| 5 | **`reshape(-1, head_v_dim)` 的展平顺序**：`z` 与 `core_attn_out` 必须同序 | **不报错**，只掉点 | 加一个 shape-agnostic 的自检：`norm(x, z)` vs `norm(x, roll(z))` 的输出必须不同 |
| 6 | **`h` 中间缓冲 67 MB/层（S=8K）** | prefill 走 HBM，134 MB 流量/层 | 融合 `chunk_fwd_h` + `chunk_fwd_o`，块状态驻留 SRAM |
| 7 | **`chunk_size` 只支持 16/32/64**（`chunk_fwd.py:369`） | 传 128 直接 raise | 固定 64，短序列 pad 到 64 的倍数 |
| 8 | **`S % 64 = 1` 也要跑满一块** | 首 token 延迟 ≈ 满块延迟 | 测试 `S = 1, 63, 64, 65` |
| 9 | **`solve_triangular` 在导出模式下退化成 64 步 Python 循环**（`:383–390`） | 编译期爆炸 | 自研后端直接实现 BC=16 三段块求逆 |
| 10 | **`0 × inf = NaN`**（`chunk_fwd.py:177–180` 的真实注释） | `e^{−1008}` 下溢为 0、`e^{+1008}` 溢出为 inf，交叉相乘出 NaN | 边界掩码 + `exp` 前的 clamp |
| 11 | **`g` 必须 ≤ 0**（文档 `:305`） | `g>0` → 状态指数发散，18 层后爆炸 | 硬件加 `min(g, 0)` 哨兵（或测试断言） |
| 12 | **`H_k = H_v = 16`，GQA 比为 1** | 照抄 Qwen3-Next 的 32:16 会 shape 错 | 从 config 读，不硬编码 |
| 13 | **`mamba_ssm_dtype=float32` 在本文件未被引用** | 误以为"config 已保证 FP32" | 实际的 FP32 来自 `:325/:445` 的显式 `.to(float32)`；cache 必须直接 FP32 分配 |
| 14 | **`is_recording_past` 强制走 prefill 路径**（`:563`） | 投机解码从 O(1) 退化到 O(S) | 专门覆盖这条路径的 perf 用例 |
| 15 | **`torch.split` 产生非连续视图**（行 stride 6144） | 下游 kernel 若假设连续，会隐式物化 3×12 KB | 在 A④ 输出处就按 128 粒度分块 DMA |
| 16 | **A⑪ 的 `weight` 只有 128 个参数**（不是 2048） | 按 2048 分配 → 读越界/错值 | 编译期检查 `norm.weight.numel() == head_v_dim` |
| 17 | **L2 norm 的 `eps` 在 sqrt 内，RMSNorm 的 `eps` 在 mean 后** | 复用同一微码 → `eps` 差 128 倍 | 两份微码，或参数化 `eps_mode` |
| 18 | **MXFP8 的 block-scale 在 q/k 上会被 L2 吸收** | 这既是优点也是陷阱：**v 分支没有这个保护** | v 的量化误差必须单独评估 |
| 19 | **`out_proj` 输入是 head-major `[16,128]`** | 若上游改成 head-minor，权重列错位 | 离线转置权重列，或显式转回 |
| 20 | **残差流 BF16 累积** | 48 次残差加 → 2.7% 误差 | FP32 残差流，或 GEMM epilogue 里融合加法 |

---

### 6.1.18 与层型 B（Gated Attention）的对照（给划分用）

| 维度 | 层型 A：Gated DeltaNet（18 层） | 层型 B：Gated Attention（6 层） |
|---|---|---|
| 每 token MAC | **59.62 M**（decode）/ 60.30 M（prefill） | 见 6.2（≈ 62 M，待核定） |
| KV cache | **无**（只有 1 MiB 状态 + 36 KiB conv 状态） | 有（随 S 线性增长） |
| 跨 token 串行 | **有**：递推 `S_t ← f(S_{t−1})`，深度 = S（decode）/ S/64（prefill） | **无**：KV 追加是纯写，注意力是 `O(S)` 并行 |
| 位置编码 | **无** | mRoPE（见 5.x） |
| 矩阵引擎占比 | **98.6%** MAC，但 1.32% 的串行部分决定延迟 | 更低（softmax 不可上引擎） |
| 关键精度约束 | **循环状态 FP32**（残差减法病态） | softmax FP32（max-subtraction） |
| 最适合放引擎的 | `in_proj_qkv`(12.58M) + MLP(37.75M) | QKV 投影 + MLP |
| 最适合放 VPU 的 | 递推单元（需状态常驻）+ conv + 两个 norm | softmax + RoPE + 门控 |

层型 A 的算力账是 98.6% 的 GEMM 加 1.4% 的串行，但延迟账上那 1.4% 决定一切。MXFP8 引擎应专注把 `in_proj_qkv`、MLP、`out_proj` 的权重带宽吃满（decode 算术强度约 1 MAC/B），而 VPU 侧的递推单元必须做到 1 MiB 状态常驻，且每 token 的 4 个依赖 pass 都不落 DRAM。这是 decode 性能的主要杠杆。

---

## 6.2 层型 B：Gated Attention（6 层）

一层 B 由 4 个投影（`q_proj` 双宽）、2 个 head-wise RMSNorm、25% 部分 RoPE、2 个 GEMV、1 次 FP32 softmax 和 1 次逐通道门控组成。

成本重心不在权重，而在 S：S=4096 时 `Q·Kᵀ` 与 `A·V` 各 8.39 M MAC，两者合计 16.78 M，已经超过全部投影之和（14.68 M）；S=262,144 时单层仅打分就要 536.9 M MAC。

**记账口径**（与第 4 章一致，保证合计可复算到 52,433,408）：
- GEMM/GEMV：1 MAC = 1 次乘加，`MAC = N_out × K`。
- 归一化 / 逐元素：按“处理元素数”记 1，仅占位（真实运算是元素的 3–6 倍 flop，但每层 <0.02 M，不影响任何结论）。
- 形状按 **decode（M=1, B=1）** 写；prefill 把 M=1 换成 M=S。

**图 6.2-1　一层 B 的张量流（L 号为 `/tmp/m35.py` 行号）**

```
x [2048]
 ├─ input_layernorm ......................... B①   L872
 ├─ q_proj [4096,2048] ...................... B②   L774  → y[4096]
 │    view(*,8,512) → chunk(2,-1) → q[8,256] , g[8,256]
 ├─ k_proj [512,2048] / v_proj [512,2048] ... B③   L780  → k[2,256] , v[2,256]
 ├─ q_norm[256] / k_norm[256] (per head) .... B④   L779
 ├─ RoPE(64 of 256) + mRoPE 重组 ............ B⑤   L784
 ├─ cache.update(K,V) ....................... B⑥   L787
 ├─ Q·Kᵀ · (1/16) ........................... B⑦   L724  → s[8,S]
 ├─ + causal mask → softmax(FP32) ........... B⑧   L726-728
 ├─ p·V ..................................... B⑨   L730  → o[8,256]
 ├─ o ⊙ sigmoid(g) .......................... B⑩   L805
 ├─ o_proj [2048,2048] ...................... B⑪   L807
 └─ + residual .............................. B⑫   L893
```

---

### B① 前置 RMSNorm

**作用**：对残差流做 per-token 归一，压掉跨层幅值漂移，为 q/k/v 三个投影提供统一量纲的输入。

**调用**：`Qwen3_5DecoderLayer.forward`（L872）→ `self.input_layernorm(hidden_states)` → `Qwen3_5RMSNorm.forward`（L837，类上挂 `@use_kernel_forward_from_hub("RMSNormZeroCentered")`，可被 hub kernel 顶替）。

**公式**：

```
μ₂(t) = (1/2048) · Σ_{i=0}^{2047} x[t,i]²
r(t)  = 1 / sqrt( μ₂(t) + 1e-6 )
y[t,i] = x[t,i] · r(t) · (1 + w[i])          # w 初始化为 0 → 初始恒等
```

注意 eps 在 mean **之后**、sqrt **之内**；乘数是 `1+w` 而不是 `w`（zero-centered）。

**形状**：输入/输出 `[B,S,2048]`，decode 时 `[1,2048]`；`w [2048]`。

**MAC 推导**：2048 次平方 + 2047 次加 + 1 次 rsqrt + 2×2048 次乘 ≈ 6.1 K 运算；记账值 **2,048**。

**精度**：平方和与 rsqrt **必须 FP32**。FP16 下 `x²` 在 `|x| > 256` 时即溢出（256² = 65,536 > FP16 max 65,504），而 Qwen 残差流存在幅值远超 256 的通道 （视 checkpoint 而定）；2048 项 FP16 累加的统计误差约 `√2048 · 2⁻¹¹ ≈ 2.2%`，直接反映到 rsqrt 上。输出降回 FP16。

**坑**：
- `(1+w)` 的恒等点是 `w=0 → 乘 1.0`。若把 `w` 量化成 FP8，块内全是 ±0.05 的小值时 E4M3 在 0.05 处步长 0.00625 → 12% 相对误差，但折算到增益只有 0.6%（因为乘以 1±0.05）。可接受，但 norm 权重每层只有 2 KB，**没有理由省**，留 FP16。
- `eps=1e-6` 放在 mean 之后，与 LayerNorm 的 `eps` 位置不同，照抄 LayerNorm 会差一个量级。
- 这是 2048 深的单 token 归约，是纯延迟点（每层 2 次：B① 与 post_attention_layernorm）。

---

### B② q_proj：双宽投影（Q + gate 一次算）

**作用**：一次 GEMM 同时产出 8 个头的 query 与 8 个头的**输出门控** gate。

**调用**：`Qwen3_5Attention.forward` L774–777：

```python
query_states, gate = torch.chunk(
    self.q_proj(hidden_states).view(*input_shape, -1, self.head_dim * 2), 2, dim=-1)
gate = gate.reshape(*input_shape, -1)
```

模块定义 L748：`nn.Linear(2048, 8*256*2 = 4096, bias=False)`。

**公式**：

```
y = W_q · x ,              W_q ∈ R^{4096×2048} ,  y ∈ R^{4096}
按 view 的语义切块：      y = [y_0 ‖ y_1 ‖ … ‖ y_7] ,  y_h ∈ R^{512}
q[h,d] = y[512h + d]           ,  d = 0..255
g[h,d] = y[512h + 256 + d]     ,  d = 0..255
```

**形状**：`x [B,S,2048]` → `y [B,S,4096]` → `q [B,S,8,256]`（转置成 `[B,8,S,256]`）、`g [B,S,2048]`（一直活到 B⑩）。

**MAC 推导**：`N·K = 4096 × 2048 = 8,388,608` = **8.39 M**。无 bias（`attention_bias=false`），epilogue 无加法。

**精度**：激活 FP16、权重 MXFP8（E4M3 + per-32 E8M0），FP32 累加，输出 FP16 交给 `q_norm`。相对误差 ~1%。**这一层敢上 MXFP8 的关键理由**：紧接着的 `q_norm` 会重新归一化每个头，把 q_proj 的**尺度**误差完全洗掉，只留下方向误差（~1%）。

**坑**：
- **双宽不是“两个 2048 拼起来”**。`view(...,-1,512)` 决定了每个头内部是 `[q(256) | g(256)]` 交错，而不是 `[q(2048) | g(2048)]`。硬件若按 offset 2048 去切，8 个头的 q/g 全部错位——这是静默错误，loss 只会“变差”不会报错。正确做法：输出描述符按 8 段 512 拆分，每段前 256 写 Q buffer、后 256 写 G buffer（引擎支持 strided/scatter 输出就可以零拷贝）。
- **为什么要双宽**（而不是两个 `[2048,2048]` GEMM，MAC 完全相同）：(a) 一次 kernel launch / 一次 DMA 描述符，decode 下 launch+同步开销常与 4 M MAC 同量级；(b) A 操作数（x 的 tile）在阵列里只读一次、复用 2 次，激活 SRAM 带宽减半；(c) N=4096 让权重流更连续。代价是累加器 `4096×4 B = 16 KB/token`（vs 8 KB），SRAM 紧时要权衡。
- **gate 的活跃区间是全层最长的**：B② 产生 → B⑩ 消费，中间跨 softmax。`2048×2 B = 4 KB/token/层` 必须常驻 SRAM/寄存器，调度器最容易在这里溢出。
- 不要把 gate 提前乘到 q 上：它乘的是**注意力输出**（B⑩），不是 q。

---

### B③ k_proj / v_proj（GQA 4:1）

**作用**：投影出 2 个 KV 头，供 8 个查询头共享（`num_key_value_groups = 8/2 = 4`，L744）。

**调用**：`self.k_proj` / `self.v_proj`（L751–756），`nn.Linear(2048, 2*256 = 512, bias=False)`；L780–781 各接一次 `view(hidden_shape).transpose(1,2)`。

**公式**：

```
k = W_k · x ,  W_k ∈ R^{512×2048}  →  k[h,d] , h = 0,1 ; d = 0..255
v = W_v · x ,  W_v ∈ R^{512×2048}  →  v[h,d]
```

**形状**：`[B,S,2048] → [B,S,2,256] → [B,2,S,256]`。

**MAC 推导**：`2 × (512 × 2048) = 2,097,152` = **2.10 M**（每支 1.05 M）。

**精度**：同 B②。差别在误差的**去向**：`k` 的误差经 B④/B⑤ 后被**冻结进 cache**，整个上下文都带着；`v` 的误差没有任何 norm 兜底，直接进 B⑨ 的加权和。若精度预算吃紧，**第一个升级回 FP16 的应该是 v_proj**（只 1.08 MB，占本层权重 2%）。

**坑**：
- 两个独立 module，但可以离线拼成 `[1024,2048]` 一次算（需重排 checkpoint）。合并后仍 2.10 M MAC，省一次 launch；对 MXFP8 引擎是 N=1024 → 8 个 128 宽 tile，比两个 N=512 各 4 个 tile 更好排流水。
- GQA 的收益在这里体现为**算力**（只算 2 个头，而非 8 个），在 B⑦/B⑨ 体现为**带宽**（KV 读 1/4）。两处收益要分开算，别重复计。
- 三层投影都无 bias，引擎 epilogue 不需要 bias add。

---

### B④ q_norm / k_norm（per-head，256 维）

**作用**：对**每个头的 256 维**单独做 zero-centered RMSNorm，抑制 prefill 时 Q/K 幅值发散导致的 logit 爆炸（Qwen3 系 QK-Norm 的标准作用）。

**调用**：L779–781，**先 norm 再 transpose**：

```python
query_states = self.q_norm(query_states.view(hidden_shape)).transpose(1, 2)   # [B,S,8,256]
key_states   = self.k_norm(self.k_proj(hidden_states).view(hidden_shape)).transpose(1, 2)
```

`Qwen3_5RMSNorm(self.head_dim=256, eps=1e-6)`（L760–761，注释明写 “only on the head dim!”）。

**公式**：对每个头 h：

```
m_h = (1/256) · Σ_{d=0}^{255} q[h,d]²
q̂[h,d] = q[h,d] / sqrt(m_h + 1e-6) · (1 + w_q[d])
```

**形状**：q `[B,S,8,256]`（2048 元素）、k `[B,S,2,256]`（512 元素）；`w_q`、`w_k` 各 `[256]`，8 个 / 2 个头**共享**同一组权重。

**MAC 推导**：元素数 `8×256 + 2×256 = 2,560`；记账值 `256+256 = 512`（第 4 章按参数量口径）。真实运算 ≈ 3×2,560 = 7.7 K flop，占本层 **0.015%**。

**精度**：**FP32 强制**（`_norm(x.float())`，L838），输出降回 FP16。归约只有 256 长，但含 rsqrt，FP16 下 `m_h` 的累加误差 ~6%，不可接受。

**坑**：
- 归一化维度是 **head_dim=256**，不是 hidden 2048。写成 2048 会把 8 个头混在一起——不是精度问题，是语义崩坏（模型直接失效）。
- 归一化在 **RoPE 之前**、**KV 写入之前**。所以 cache 里存的是“已 norm、已旋转”的 K。做 KV 量化时，`(1+w)` 必须在这里就折完，不能推迟到读取时。
- 10 条独立归约（8+2）× 256 长：必须并行发射，不要串行；每条几十 cycle，串行就是 10 倍延迟。
- `w` 全零初始化 → 恒等；量化时同样要保住 `0 → 1.0`。

---

### B⑤ 部分 RoPE（mRoPE，64/256 维）

**作用**：只对每个头的前 64 维注入位置信息，后 192 维原样透传（`partial_rotary_factor=0.25`）。

**调用**：`Qwen3_5TextModel.forward` L1276 `self.rotary_emb(hidden_states, position_ids)` → `Qwen3_5TextRotaryEmbedding.forward`（L178）→ `recomposition_frequencies`（L190）→ `apply_rotary_pos_emb(q,k,cos,sin)`（L661）→ `rotate_half`（L653）。`inv_freq` 来自 `compute_default_rope_parameters`（L154）。

**公式**：

```
dim  = int(head_dim · 0.25) = int(256 · 0.25) = 64 ,  half = 32
θ_j  = 1 / (1e7 ^ (2j/64)) = 10^(−7j/32) ,  j = 0..31
θ_0  = 1.0 ,  θ_31 = 1.65e-7
cos/sin 表长 64：C[j] = C[j+32] = cos(p·θ_j)      # 由 cat((f,f), -1) 造成
half-split 旋转（GPT-NeoX / HF-Llama 约定）：
q'[j]     = q[j]·C[j] − q[j+32]·S[j]
q'[j+32]  = q[j+32]·C[j] + q[j]·S[j]        , j = 0..31
q'[64:256] = q[64:256]                       # 192 维透传
```

mRoPE 重组（`mrope_section=[11,11,10]`、`mrope_interleaved=true`）：

```
通道 j 归属的 grid： g(j) = j mod 3     (0=T, 1=H, 2=W)
代码等价于：for dim, offset in ((1,H=1),(2,W=2)): idx = slice(offset, section[dim]*3, 3)
T 通道 = {0,3,…,30}      共 11 个
H 通道 = {1,4,…,31}      共 11 个
W 通道 = {2,5,…,29}      共 10 个       11+11+10 = 32 ✓
φ_j = pos_{g(j)} · θ_j
```

**纯文本退化**：`position_ids` 的三行完全相同——文本路径 L1250 用 `arange(...).view(1,1,-1).expand(4,B,-1)` 再取 `[1:]`，生成路径 L1566/1569 用 `.repeat(3,1,1)`。因此 `freqs[0] ≡ freqs[1] ≡ freqs[2]`，`recomposition` 变成恒等操作，`φ_j = p·θ_j`，与普通 RoPE 逐位相同。**这一条对芯片很实用**：纯文本部署时可以把 mRoPE 的 3 路 gather 编译成 1 路。

**形状**：q `[B,8,S,256]`、k `[B,2,S,256]`；cos/sin `[B,S,64]` → `unsqueeze(1)` → `[B,1,S,64]` 广播到 8 / 2 个头。

**MAC 推导**：q：`8 头 × 32 对 × (4 mul + 2 add) = 1,024 mul`；k：`2 × 32 × 4 = 256 mul`；合计 **1,280 mul + 640 add ≈ 0.0013 M MAC**。另外每个 token 需 32 次 sin + 32 次 cos（重组后只有 32 个不同频率）；参考实现先对 3 个 grid 各算一遍（96 sin + 96 cos）再挑选，纯文本时是 3× 冗余。

**精度**：cos/sin 表 FP32，旋转乘加 FP32 后回 FP16。

**坑**：
- **half-split 不是 interleaved**。配对是 `(j, j+32)`，不是 `(2j, 2j+1)`。写成 interleaved 会让输出完全错误（等于换了一套位置编码），不是“轻微掉点”。自检方法：cos 表长 64 且后 32 项与前 32 项相同 → 一定是 half-split。
- **角度精度**：`φ = p·θ`，p 可达 262,144，`θ_0 = 1` → `φ ≈ 2.6e5 rad`。FP32 相对误差 2⁻²⁴ → 绝对角度误差 `262144 × 6e-8 ≈ 0.0156 rad` → cos 误差 ~1.6%。必须在 FP64（或“整数 mod 2π + 双精度乘”）下算，否则 256K 上下文的高频通道相位是错的 （低频通道无所谓，它本来就在剧烈 alias）。
- **全表缓存不可行**：`262,144 × 64 × 2(cos,sin) × 2 B = 67.1 MB`。只能按 chunk 生成，或按 tile 现算。
- RoPE 在 **KV 写入之前**（L784 → L787），cache 里是旋转后的 K；增量解码时只旋转新 token，历史 K 不再旋转。
- `recomposition_frequencies` 里有 in-place 写（`freqs_thw[..., idx] = freq[dim, ..., idx]`），依赖 `freq` 是新建张量而不是 expand 视图；在 `@torch.no_grad()` 下安全，但复用到自定义 kernel 时要注意别名。

---

### B⑥ KV cache 追加

**作用**：把本层“已 norm、已旋转”的 K 与未旋转的 V 追加到 cache。

**调用**：L786–787 `past_key_values.update(key_states, value_states, self.layer_idx)`（`DynamicCache.update`，按 `layer_idx` 定位）。

**公式**（每层每 token 字节数）：

```
B_layer = 2(K,V) × n_kv(2) × d_h(256) × b(dtype)
FP16 → 2 × 2 × 256 × 2 = 2,048 B / 层 / token
6 层 → 6 × 2,048 = 12,288 B / token
S 字节 → 12,288 · S
```

**形状**：cache 布局 `[B, 2, S, 256]` × {K, V}，6 份（层 3/7/11/15/19/23）。

**MAC 推导**：**0**。纯搬运，归 DMA。

**精度**：K/V 存 FP16。若改 FP8（E4M3 + per-head 或 per-32 scale），容量减半，但 K 的误差会一直留到该 token 被淘汰，且 B⑦ 会再吃一次量化误差 ⇒ **不建议 KV 用 FP8，除非同时把 QKᵀ 留在 FP16 并做 per-channel 校正** 。

**坑**：
- `DynamicCache` 是**增长式**的：容量不足时整块 realloc + copy（S 翻倍时一次搬 2× 数据）。芯片上必须定长 ring buffer 或 paged（block=128 token），否则 256K 上下文会出现 O(S) 搬运尖峰。
- K 已旋转、V 未旋转；两者不能共用一套量化/norm 流水。
- 性能天花板在这里，不在 MAC：S=4096 时一次 decode 读 50.3 MB（@1 TB/s → 50 µs → ~20 K tok/s 上限）；S=262,144 时 3.22 GB（3.2 ms → ~310 tok/s）。

---

### B⑦ Q·Kᵀ（打分）

**作用**：每个查询头与全部历史位置做点积，得到匹配分数。

**调用**：L789–802 `ALL_ATTENTION_FUNCTIONS.get_interface(self.config._attn_implementation, eager_attention_forward)`；HF 默认 `_attn_implementation="sdpa"`，eager 路径为 `eager_attention_forward`（L711）中的 `torch.matmul(query, key_states.transpose(2,3))`；前置 `repeat_kv(key, 4)`（L699）。

**公式**：

```
s[h,t] = α · Σ_{d=0}^{255} q[h,d] · k[h//4, t, d] ,     α = 1/√256 = 1/16 = 0.0625
repeat_kv：k_rep[h] = k[h//4] ,  h = 0..7（expand+reshape，stride=0 的逻辑视图，不复制数据）
```

`scaling` 来自 L745 `self.head_dim ** -0.5`。α = 2⁻⁴ 在二进制里**精确**。

**形状**：q `[B,8,1,256]`；k `[B,2,S,256] → [B,8,S,256]`；s `[B,8,1,S]`。

**MAC 推导**：`8 × 256 × S = 2,048·S`。
S=4096 → `8,388,608` = **8.39 M**；S=1024 → 2.10 M；S=262,144 → 536.9 M。

**精度**：FP16 输入、**FP32 累加**、结果 FP16 存 score。α 可折进 Q（省 8S 次乘法，且 2⁻⁴ 精确无误差）。

**坑**：
- `repeat_kv` 的 4× 是**逻辑**复制。若硬件按 8 个头各读一份 KV，KV 带宽变成 4×（S=4096 时每层 8.39 MB → 33.6 MB）。正确做法：2 个 KV 头常驻 SRAM/寄存器，8 个 Q 头复用。
- decode 是纯 GEMV（M=1）：128 行阵列只用 1 行，利用率 1/128 = 0.8%。放矩阵引擎是浪费（见下文 MK 准入）。
- `key_states.transpose(2,3)` 是 stride 视图不搬数据；若引擎要求 K 以 `[d, S]` 行主序，就该在 B⑥ 写入时**转置存**，否则每次 decode 都要转一遍 `2×256×S`。
- α 不能在 FP16 下与 mask 混算：mask 用 `finfo.min`，`−65504 × 0.0625` 会被“抬起来”，所以代码顺序是**先缩放再加 mask**（L724→726）。

---

### B⑧ 因果 mask + softmax

**作用**：上三角屏蔽未来位置，按行归一化成概率分布。

**调用**：L725–728

```python
attn_weights = attn_weights + attention_mask
attn_weights = nn.functional.softmax(attn_weights, dim=-1, dtype=torch.float32).to(query.dtype)
attn_weights = nn.functional.dropout(attn_weights, p=dropout, training=module.training)
```

mask 由 `Qwen3_5TextModel.forward` L1270–1273 的 `create_causal_mask(**mask_kwargs)` 生成，按 `layer_types` 分成 `full_attention` / `linear_attention` 两套。

**公式**：

```
s'[h,t] = s[h,t] + M[t] ,   M[t] = 0 (t ≤ t_cur) ,  M[t] = −inf (t > t_cur)
m[h]    = max_t s'[h,t]
p[h,t]  = exp(s'[h,t] − m[h]) / Σ_{τ=1}^{S} exp(s'[h,τ] − m[h])
```

**形状**：`s, p [B,8,1,S]`（decode）；prefill 为 `[B,8,S,S]`。

**MAC 推导**：mask 加 `8S` + max 归约 `8S` + exp `8S` + 求和 `8S` + 除法 `8S` ≈ **40·S**（核心项记 `32·S`）。S=4096 → 0.16 M，占本层 0.2%，但**全是串行归约 + 超越函数**，是延迟热点而不是算力热点。

**精度**：**FP32 强制**（`dtype=torch.float32`），结果再降回 FP16 给 B⑨。

**数值稳定性论证（为什么必须减 max）**：
- 不减 max 时，exp 的上溢阈值：FP16 为 `ln(65504) = 11.09`，FP32 为 `ln(3.4e38) = 88.7`。scaled 后 logit 的典型标准差是 1（`std ≈ √256·σ²/16`，σ=1 时恰为 1），S=4096 的极值约 3.5σ ≈ 3.5，看似安全。
- 但 Q/K 存在幅值 outlier，单个 logit 到 30–60 并不罕见 → FP16 直接 inf，FP32 也已逼近 88。
- 减 max 后最大项恰为 `exp(0) = 1`，分母 ∈ `[1, S]`，**任何输入都不会上溢**；小项 `exp(−30) ≈ 9.4e-14` 在 FP32 里仍是非零（FP32 最小正规 1.2e-38），而 FP16 在 `exp(−17)` 就下溢成 0，会把长尾注意力直接抹掉。
- 只减 max 还不够：若 p 用 FP16 存，S 项舍入误差最坏 `S × 4.9e-4`（S=4096 → 2.0），所以归一化除法必须在 FP32 完成。

**坑**：
- mask 通常不是字面 `-inf`，而是 `torch.finfo(dtype).min` （在 `masking_utils.py`，需按 transformers 版本核对）。后果：(a) 必须“先缩放、再加 mask”，否则 min 值被缩放后不再压倒一切；(b) 整行被 mask（padding-only）时得到全 `min` → softmax 输出**均匀分布**而不是 NaN，是静默错误。芯片上应对“整行无有效项”显式输出 0。
- eager 路径会把 `attn_weights` 物化并返回：S=262,144 时 `8 × 262144 × 4 B = 8.39 MB`/层/token 的临时缓冲，必须 tile 成 flash 式在线 softmax（维护 running max / running sum，SRAM 只需 O(Bc)）。
- `attention_dropout=0.0` 且推理时 `dropout=0.0`，`F.dropout` 是空操作，硬件不留通路。
- exp 需要 SFU：`8 × S = 32,768` 次 exp / token / 层，6 层共 196,608 次。若 VPU 16 lane、每 cycle 1 次 exp，就是 12,288 cycle ≈ 6.1 µs @2 GHz （取决于阵列规模）——比同层所有 GEMM 的算力时间还长。**attention 慢在非线性，不在乘加**。

---

### B⑨ A·V（加权求和）

**作用**：用概率对 V 做加权平均，把 S 个位置的信息压回每头 256 维。

**调用**：L730–731 `torch.matmul(attn_weights, value_states)` → `attn_output.transpose(1,2).contiguous()`。

**公式**：

```
o[h,dv] = Σ_{t=1}^{S} p[h,t] · v[h//4, t, dv] ,   dv = 0..255
```

**形状**：`p [B,8,1,S] × v [B,8,S,256] → [B,8,1,256]` → transpose → `[B,1,8,256]` → reshape `[B,1,2048]`。

**MAC 推导**：`8 × S × 256 = 2,048·S`，与 B⑦ 对称。S=4096 → **8.39 M**。

**精度**：p 来自 FP32 softmax 降位、v FP16、**FP32 累加**。S 项 FP16 累加误差 ~`√S·2⁻¹¹`（S=4096 → 3%），不可接受。若 p 用 MXFP8（3% 元素相对误差），加权平均**不改善**相对精度（输出 ≈ 权重元素的相对误差），仍是 ~3%——比 FP16 差 64×，这是不建议 p 走 FP8 的核心理由。

**坑**：
- 形状是 M=8、N=256、K=S 的 GEMM。K 长利于流水，但 M=8 对 128 行阵列仍是 6% 利用率。**prefill 时 M=S 才变成真正的大 GEMM**（S×S×256），那时才值得上引擎。
- `transpose(1,2).contiguous()` 会真搬一次数据（`[8,256] → [256,8]` 布局，2048 元素/token），reshape 后对齐 o_proj 的 K 维（head-major：`head0_d0..head0_d255, head1_d0..`）。把 o_proj 权重按这个顺序重排，可以把这个 transpose 彻底消掉（离线一次，运行时零成本）。
- V 的 4× 复用同 B⑦：8 个 Q 头共享 2 个 V 头，别重复读。

---

### B⑩ 输出门控（sigmoid，在 o_proj 之前）

**作用**：用 B② 产出的 gate 对注意力输出做**逐通道**（不是逐头）门控。

**调用**：L804–805，`attn_output = attn_output * torch.sigmoid(gate)`，位于 `self.o_proj` **之前**（L807）。

**公式**：

```
o'[i] = o[i] · σ(g[i]) ,   σ(g) = 1 / (1 + e^{−g}) ,   i = 0..2047
```

**形状**：o、g 均 `[B,S,2048]`（`gate.reshape(*input_shape, -1)` 把 `[8,256]` 拉平成 2048）。

**MAC 推导**：2,048 次 sigmoid（每次 ≈1 exp + 1 div）+ 2,048 次乘；记账值 **2,048**。

**精度**：sigmoid 在 FP32 算、乘在 FP32 算，再降 FP16 送 o_proj。FP16 直接算 sigmoid 的绝对误差 ~5e-4（σ≈0.5 处即 0.1% 相对），能省但要确认。

**坑**：
- **顺序是定义性的**：门控在 `o_proj` **之前**。放到之后数学上完全不等价——门控是逐 2048 通道，o_proj 是 2048×2048 混合。这是 Qwen3-Next 系 gated attention 最容易写错的一处，也是与“普通 Qwen3”最大的结构差别。
- gate 是 **per-channel**，不是 per-head。`[8,256]` 展平后第 `h*256+d` 个通道对应 o 的第 `h*256+d` 维。
- 饱和：`|g| > 8` 时 σ 已是 0.99966 / 3.4e-4，FP16 下会硬饱和到 1.0 / 0.0，等于把该通道关断。若把 gate 量化到 FP8，E4M3 在 8 附近步长为 1，σ 的细微变化被完全抹平 → **gate 走 FP16，不要 FP8**。
- gate 的 4 KB/token/层 与残差流的 4 KB 同时活跃，是 SRAM 预算里最容易被忽略的一项。

---

### B⑪ o_proj

**作用**：把 8 个头拼接后的 2048 维混合回残差流。

**调用**：L807 `attn_output = self.o_proj(attn_output)`，`nn.Linear(2048, 2048, bias=False)`（L757）。

**公式**：`y = W_o · o'`，`W_o ∈ R^{2048×2048}`。

**形状**：`[B,S,2048] → [B,S,2048]`。

**MAC 推导**：`2048 × 2048 = 4,194,304` = **4.19 M**。

**精度**：输入 MXFP8（DMA 在线量化 B⑩ 的 FP16 结果），FP32 累加，输出 **FP32** 直通残差。

**坑**：
- 输入动态范围被 B⑩ 的 σ 拉得很开（有的通道 ×1，有的 ×1e-4），per-32 block scale 会把同块内的小通道压向 subnormal。E4M3 的 subnormal 下限是块内最大值的 2⁻⁹；块内动态范围超过 512× 就丢通道。若 gate 分布很宽，o_proj 用 FP16 或 per-16 更细的 scale 更稳。
- K 维顺序必须与 B⑨ 的 reshape 一致（head-major）；权重重排免费，但忘了就是全错。
- 输出 FP32：这是残差前最后一次干净的舍入机会，别在这里截成 FP16 再相加。

---

### B⑫ 残差加

**作用**：把注意力分支加回残差流。

**调用**：`Qwen3_5DecoderLayer.forward` L870 `residual = hidden_states` → L893 `hidden_states = residual + hidden_states`。

**公式**：`h'[t,i] = h[t,i] + o_proj_out[t,i]`。

**形状**：`[B,S,2048]`。

**MAC 推导**：2,048 次加，**0 MAC**。

**精度**：FP32 相加，结果存 FP16（下一层 B① 会再升 FP32）。

**坑**：残差流必须全程活跃（4 KB/token），且与 gate（4 KB）、q（4 KB）、score/输出累加器同时存在。本层的 SRAM 预算要按“权重流 + 6 份 4 KB 活跃张量”算，只看权重会低估一倍以上。

---

### 6.2.1 层型 B 单 token 合计

| 算子 | MAC | 类型 | 可上 MK（MXFP8） |
|---|---:|---|---|
| B① input_layernorm | 2,048 | 归约 | 否，VPU |
| B② q_proj | 8,388,608 | GEMM 4096×2048 | 是 |
| B③ k_proj + v_proj | 2,097,152 | 2×GEMM 512×2048 | 是 |
| B④ q_norm + k_norm | 512（2,560 元素） | 10×归约 256 | 否，VPU |
| B⑤ RoPE（64/256） | ~1,280 mul | 逐元素加三角 | 否，VPU |
| B⑥ KV 追加 | 0 | DMA | 否 |
| B⑦ Q·Kᵀ | **2,048·S** | GEMV（M=1） | 否 |
| B⑧ mask + softmax | ~40·S | 归约加 exp | 否，VPU |
| B⑨ A·V | **2,048·S** | GEMV（M=8） | FP16 可，FP8 不建议 |
| B⑩ 门控 sigmoid | 2,048 | 逐元素加 exp | 否，VPU |
| B⑪ o_proj | 4,194,304 | GEMM 2048×2048 | 是 |
| B⑫ 残差 | 2,048 | 逐元素 | 否，VPU |
| post_attention_layernorm | 2,048 | 归约 | 否，VPU |
| MLP（见 6.3） | 37,748,736 | 3×GEMM | 是 |
| **合计** | **52,433,408 + 4,096·S** | | |

复算：`8,388,608 + 2,097,152 + 4,194,304 + 37,748,736 = 52,428,800`，加 4 个 norm 的 4,608 → **52,433,408**；注意力项 `2×2,048·S = 4,096·S`。

**即 52.43 M + 4,096·S MAC / token / 层。**

| S | QKᵀ | A·V | 注意力小计 | 单层合计 | ×6 层 |
|---:|---:|---:|---:|---:|---:|
| 1,024 | 2.10 M | 2.10 M | 4.19 M | 56.63 M | 339.8 M |
| **4,096** | **8.39 M** | **8.39 M** | **16.78 M** | **69.21 M** | **415.3 M** |
| 32,768 | 67.11 M | 67.11 M | 134.22 M | 186.65 M | 1.12 G |
| 262,144 | 536.87 M | 536.87 M | 1,073.74 M | 1,126.18 M | **6.76 G** |

三个可以直接引用的比值：
- S=4096：注意力（16.78 M）> 全部投影（q 8.39 + kv 2.10 + o 4.19 = **14.68 M**）。**投影不再是主角。**
- S=4096：注意力占本层算力 24.2%；S=262,144 时占 **95.3%**。
- 24 层里只放 6 层全注意力，正是为了让 `4,096·S` 这一项乘 6 而不是乘 24。

### 6.2.2 KV cache 三档

`12,288 B/token = 6 层 × 2(K,V) × 2 头 × 256 维 × 2 B`

> 下表用 1 TB/s 做**对照刻度**，便于看趋势；本文系统的档位是 310 GB/s（见 8.2），除以 3.2 即可。

| S | 容量 | 单次 decode 读量 | @1 TB/s（对照） | 等效 tok/s 上限 |
|---:|---:|---:|---:|---:|
| 4,096 | 50,331,648 B = **50.3 MB** | 50.3 MB | 50 µs | ~20 K |
| 32,768 | 402,653,184 B = 402.7 MB | 402.7 MB | 403 µs | ~2.5 K |
| 262,144 | 3,221,225,472 B = **3.22 GB**（3.0 GiB） | 3.22 GB | 3.2 ms | ~310 |

对照层型 A：18 层 DeltaNet 的状态恒为 18.87 MB（FP32），**与 S 无关**。这就是 18:6 配比的物理意义——把线性增长的项压到 1/4 的层数上。

### 6.2.3 本层权重带宽（MXFP8，1.031 B/参数）

| 张量 | 参数 | MXFP8 字节 |
|---|---:|---:|
| q_proj [4096,2048] | 8,388,608 | 8,650,752（8.65 MB） |
| k_proj [512,2048] | 1,048,576 | 1,081,344（1.08 MB） |
| v_proj [512,2048] | 1,048,576 | 1,081,344（1.08 MB） |
| o_proj [2048,2048] | 4,194,304 | 4,325,376（4.33 MB） |
| 注意力小计 | 14,680,064 | **15,138,816（15.14 MB）** |
| MLP 3×[6144,2048] | 37,748,736 | 38,928,384（38.93 MB） |
| **单层** | **52,428,800** | **54,067,200（54.07 MB）** |

- 6 层权重 = `54,067,200 × 6 = 324,403,200 B = 324.4 MB`/token。@1 TB/s → 324 µs → **~3.1 K tok/s** 上限（仅这 6 层）。
- 加上 S=4096 的 KV 50.3 MB → 374.7 MB，~375 µs，**~2.7 K tok/s**。
- 交叉点：单层注意力权重 15.14 MB vs KV 读 `2,048·S`，**S ≈ 7,400** 时相等；整层权重 54.07 MB vs KV，**S ≈ 26,400** 时相等。S=4096 时权重是主角，S=256K 时 KV 是主角（3.22 GB vs 324 MB）。

### 6.2.4 MK（MXFP8 矩阵引擎）准入：哪些算子不能上

> 记号：**MK = 本方案的 MXFP8 矩阵引擎**（E4M3 + per-32 E8M0 块缩放、FP32 累加）。若你的术语表里 MK 另有定义，仅替换名称，结论不变。

**可以上的算子（本层只有 5 个）**：`q_proj`、`k_proj`、`v_proj`、`o_proj`，以及 MLP 的三个投影。它们合计 52.43 M MAC、54.07 MB 权重，是本层唯一算力密集且形状规整的部分。

**不能上的算子**：

| 算子 | 否决理由 | 归属 |
|---|---|---|
| **softmax**（B⑧） | MK 只有 `Σ a·b` 一种原语，没有 max 归约、没有 exp、没有按行除法；且必须 FP32（FP16 在 `exp(−17)` 下溢、`exp(11)` 上溢）。这是**指令集层面**的不匹配，不是精度问题。 | VPU（需 SFU） |
| **RoPE**（B⑤） | 算术强度 ≈1 mul/元素，无数据复用；half-split 的配对跨 32 元素 stride，MK 的 A/B 操作数布局表达不了；还要 sin/cos 与本层唯一的 FP32 角度精度要求。 | VPU（或预生成表） |
| **Q·Kᵀ**（B⑦） | ① **形状**：decode 时 M=1，128×128 阵列只用 1 行 → 利用率 0.8%，比 VPU 还慢；② **精度**：E4M3 的 3 bit 尾数（RMS 相对误差 ε≈3×10⁻²）使 logit 误差 `δs ≈ ε·(‖q‖‖k‖/√256)·α = 0.03×16/16 = 0.03`，而 FP16 只有 4.9×10⁻⁴，**差 64×**；q 侧的误差对所有 t 是共模、softmax 后会抵消，k 侧误差逐位置独立、不抵消，直接以 `e^{δ}` 的比例扰动注意力权重（3σ 尾事件 1.10×），长上下文检索时足以翻转 top-1；③ K 若已在 cache 里存 FP16，每次现量化等于多做一遍 `2×256×S` 的读写。 | VPU / 专用 FP16 dot 单元；**prefill（M=S）时才回引擎，且用 FP16** |
| **q_norm / k_norm**（B④） | 归约 + rsqrt，没有乘加结构；必须 FP32；只有 2,560 个元素，放引擎连 tile 都填不满。 | VPU |
| 因果 mask（B⑧） | 逐元素加，`8S` 次；且与 `finfo.min` 的特殊语义绑定。 | VPU |
| sigmoid 门控（B⑩） | 超越函数 + 逐元素乘；2,048 个元素。 | VPU（SFU） |
| 残差加（B⑫） | 纯加法，FP32，必须贴着残差寄存器做。 | VPU / ALU |
| KV 追加（B⑥） | 无 MAC。 | DMA |

**边界算子：A·V（B⑨）**
- 形状 M=8 → 128 行阵列 6% 利用率，decode 下上引擎不划算。
- 精度：p 的 3% 元素相对误差经加权平均**不会变小**（输出相对误差 ≈ 权重元素的相对误差），仍 ~3%，是 FP16 的 64×。
- 建议：decode 走 FP16 的 VPU/专用路径；prefill（M=S、S×S×256 的真 GEMM）可以上引擎，但用 FP16 模式或 FP8 + 残差校正 （需实测）。

这一层能上矩阵引擎的是 4 个投影（外加 MLP 的 3 个），它们贡献 100% 的权重带宽和 80% 的 MAC。QKᵀ 和 AV 虽然 MAC 更大，但形状是 GEMV，上引擎只会在 128 行阵列里空转 127 行。由此可以定出引擎的形状偏好：N 维要宽（4096），K 维要长（2048），M 维无所谓。这正好是投影的形状，而不是注意力的形状。

### 6.2.5 十个必须记住的坑（按危害排序）

1. **门控顺序**：`attn_output * sigmoid(gate)` 必须在 `o_proj` **之前**（L805 vs L807）。放到之后不是“近似”，是完全不同的函数。
2. **RoPE 是 half-split**：配对 `(j, j+32)`，cos 表长 64 且前后 32 项相同。写成 interleaved 全盘皆错。
3. **归一化维度是 256**（head_dim），不是 2048；8 个 Q 头共享一组 `[256]` 权重。
4. **`q_proj` 的 4096 维是每头 512 交错**（`[q256|g256] × 8`），不是 `[q2048|g2048]`。
5. **先缩放再加 mask**，且 mask 是 `finfo.min` 而非 `-inf`；整行 mask 会静默变成均匀分布。
6. **softmax 必须 FP32**：减 max 保上溢，FP32 保 `exp(−30)` 的下溢，FP32 除法保 `Σp = 1`。
7. **`repeat_kv` 是逻辑复制**：8 个头共享 2 个 KV 头，硬件别读 4 遍。
8. **K 是“已 norm + 已旋转”才入 cache**；V 两者都没有。量化策略不能共用。
9. **zero-centered norm**：乘 `1+w`，`w` 初始 0 → 恒等；FP8 量化 `w` 会破坏恒等点。
10. **角度精度**：`p·θ` 在 p=262,144 时 FP32 只有 ~0.0156 rad 精度（cos 误差 1.6%），要 FP64 或整数 mod 2π。

### 6.2.6 与其它章节的接口

- 结构清单见 **3.4 / 4.2**（11 个张量、52,433,408 参数）；RoPE 的通用形式见 **5.5**；MLP 见 **6.3**；带宽/缓存总账见 **8.2 / 8.3**；量化归属见 **11.1**。
- 与层型 A 的分工：A 的串行递推是**恒定状态、每 token 固定 0.786 M**；B 的注意力是**线性增长、S=4096 时 16.78 M**。6 层 B 在 S=4096 时贡献 415.3 M MAC/层组，18 层 A 贡献 18×59.6 = 1,073 M——**短上下文时 A 更贵，长上下文时 B 更贵**，交叉点约在 S ≈ 2,600（`16.78 M vs 59.6−52.43 = 7.2 M` 的差额需要 `4,096·S ≈ 7.2 M`，即 S ≈ 1,760 该比较含 MLP 重复计数，仅作量级参考）。
- 对引擎的最终要求：N 支持 4096 且**可分段 scatter 输出**（B② 的 512 交错）、K=2048、FP32 累加、MXFP8 输入 + FP16 旁路（给 A·V 与可能的 v_proj）。

---

## 6.3 子模块 C：SwiGLU MLP（24 层共用）

MLP 是全模型最大的一块算力（906 M MAC，47.8%），也是最大的一块权重（934 MB，48.1%）。24 层结构完全同构，因此是最适合先上矩阵引擎、先做 MXFP8、先做 tiling 优化的部分。三个矩阵都是 2048↔6144，M=1 时算术强度 0.97 MAC/byte，是一道纯粹的带宽题。

```
x [2048] ──► RMSNorm ──► [g|u] = W_cat · x   (W_cat = [12288, 2048])
                              │
                              ├─ g [6144] ──► silu ──┐
                              └─ u [6144] ─────────── ⊙ ──► a [6144]
                                                          │
                                          down [2048,6144] ──► y [2048] ──► + residual
```

### M1 前置 RMSNorm
**作用**：归一化残差流后再进 MLP，防止 6144 维投影被 outlier 主导。
**调用**：`Qwen3_5DecoderLayer.forward` → `self.post_attention_layernorm(hidden_states)` → `Qwen3_5RMSNorm.forward`
**公式**：`r = x · rsqrt(mean(x²)+1e-6) · (1+w)`，见 5.1
**形状**：`[2048] → [2048]`
**MAC 推导**：6.1 K MAC（0.016% of 37.75 M）
**精度**：FP32 内部，输出 FP16（送进矩阵引擎前需 MXFP8 量化）
**坑**：这一层的输出**同时**是 gate/up 两个投影的输入，只算一次、只读一次（4 KB）。如果实现成两次独立 GEMM 各自归一，会多算 6.1 K MAC 和多一次 11 级归约延迟——这是最容易被忽略的“重复前置”。

### M2 gate/up 双投影
**作用**：把 2048 维升到 6144 维的两份：一份做门（`gate`），一份做值（`up`）。
**调用**：`Qwen3_5MLP.forward` → `self.gate_proj(x)` / `self.up_proj(x)`（`nn.Linear(2048, 6144, bias=False)` → `F.linear`）
**公式**：

```
g[n] = Σ_{k=0}^{2047} W_gate[n,k] · r[k],    n = 0..6143
u[n] = Σ_{k=0}^{2047} W_up[n,k]   · r[k]
```
拼接形式（推荐）：
```
[g ‖ u] = W_cat · r,    W_cat = [W_gate ; W_up] ∈ R^{12288×2048}
```
**形状**：权重 2×`[6144, 2048]`；输入 `[2048]`；输出 `g [6144]`、`u [6144]`（拼接后 `[12288]`）
**MAC 推导**：

```
单矩阵： 6144 × 2048 = 12,582,912 = 12.58 M
双投影： 2 × 6144 × 2048 = 25,165,824 = 25.17 M   ← 本算子
```
（MAC 定义 = 1 乘 + 1 加；折 FLOP 要 ×2 → 50.3 MFLOP。）
**精度**：输入 MXFP8（E4M3，per-32 block scale），**FP32 累加**，输出 **FP16**。
**为什么输出 FP16 而不是 FP32/FP8**：
- 下游是 `silu` + 逐元素乘，FP16 的 11-bit 尾数给出 4.9e-4 相对精度，`silu` 的 LUT 误差（6.1e-5）远小于它，不构成瓶颈；
- 缓冲大小：6144×2B = **12 KB/张**，两张 24 KB，能整块驻 SRAM 并和 VPU 共享；FP32 要 48 KB，直接翻倍 SRAM 压力，而精度收益为零（因为下游马上要做舍入）；
- **不能 FP8**：理由见 5.3 的三条（负半轴档位、误差预算、零收益）。
**坑**：
1. **拼接的收益是 tiling 效率，不是带宽**。两次 N=6144 和一次 N=12288，读 `r` 都是 4 KB（0.2% 的流量），真正的收益是：少一次 kernel launch、权重连续（一次 DMA 描述符覆盖 12288×2048）、N 维 tile 对齐更规整。**代价**：checkpoint 里 `gate_proj` 与 `up_proj` 是两个独立张量，需要在**加载时 repack 成 `[12288,2048]`**（24 层 × 25 MB 的一次性成本，可离线做）。
2. **输出布局要提前定**。拼接 GEMM 的输出是 `[g(0..6143) ‖ u(0..6143)]`，`silu` 阶段要跨 6144 元素取配对 → 两个 12 KB buffer 各读一遍。若把权重按 **interleaved（每 32 个 gate 紧跟 32 个 up）** 重排，VPU 可在同一 cache line 内取到配对，silu 的访存减半。这是 repack 时顺手就能做的事，值得做。
3. `bias=False`（源码显式写死）。别给矩阵引擎配 bias 累加器初值，白搭一个加法器。
4. M=1 的 GEMV：`N=12288, K=2048`。K 只有 2048，一次就能装进 tile，所以引擎的瓶颈在 **N 方向的输出带宽**与权重读取，不在累加链长度。**算术强度 = 25.17 M MAC / 25.17 M 权重 ≈ 1 MAC/参数 = 0.97 MAC/byte**——这就是“必须靠 batch 或投机解码才能救”的根源。

### M3 SwiGLU 门控
**作用**：`gate` 分支当软开关，逐通道调制 `up` 分支。
**调用**：`Qwen3_5MLP.forward` 里的 `self.act_fn(self.gate_proj(x)) * self.up_proj(x)` → `F.silu` + 逐元素乘（VPU）
**公式**：`a[n] = silu(g[n]) · u[n] = g[n]·σ(g[n])·u[n]`，n = 0..6143
**形状**：`[6144] ⊙ [6144] → [6144]`
**MAC 推导**：6144 次 silu（≈3 拍 LUT）+ 6144 乘 = **约 12.3 K MAC + 6.1 K 乘**
**精度**：FP16 输入输出；`silu` 内部可用 FP32 中间（LUT 索引 + FMA）
**坑**：见 5.3 全部。补充一条：**`g` 与 `u` 必须来自同一次 `r`**（同一次归一），否则两个分支看到的输入差一个舍入，门控会偏。

### M4 down 投影
**作用**：把 6144 维压回 2048，回到残差流宽度。
**调用**：`self.down_proj(...)`（`nn.Linear(6144, 2048, bias=False)`）→ `F.linear`
**公式**：`y[m] = Σ_{n=0}^{6143} W_down[m,n] · a[n]`，m = 0..2047
**形状**：权重 `[2048, 6144]`；输入 `[6144]`；输出 `[2048]`
**MAC 推导**：

```
2048 × 6144 = 12,582,912 = 12.58 M
```
**精度**：输入 MXFP8（由 DMA 把 M3 的 FP16 结果**在线量化**），**FP32 累加**，输出 **FP32**。
**为什么输出必须是 FP32**：
- 它直接进残差加。残差流在 Qwen 里存在数百量级的 outlier，FP16 在 512 处的 ulp 是 0.5——一个 O(1) 的 MLP 增量会被**整个吞掉**（swamping）。这不是“精度略降”，是“这一层的贡献消失”。
- 24 层串联，每层 FP16 舍入 2.4e-4 相对误差，随机游走累计 √24×2.4e-4 ≈ 1.2e-3，再乘 outlier 幅值 → 绝对误差 O(1)。FP32 残差主干是底线。
- 建议：**残差主干全程 FP32**，只在“进入 GEMM 之前”量化成 MXFP8，在“GEMM 累加器”里保持 FP32，在“逐元素”里降到 FP16。三段式精度策略，一处都不能反。
**坑**：
1. 输入量化是**在线**的（silu 输出 → E4M3 + per-32 scale），需要一个 32 元素的 amax + 倒数 scale，属于串在关键路径上的小归约；把它和 silu 融合在同一个 pass 里，别单独跑一遍。
2. `[2048, 6144]` 的 K=6144，比 gate/up 的 K 长 3 倍——累加链更长，FP32 累加器的位宽/进位链要够（这也是它必须 FP32 累加的另一个理由）。

### M5 残差加
**作用**：把 MLP 输出并回主干。
**调用**：`Qwen3_5DecoderLayer.forward` → `hidden_states = residual + hidden_states`（893/899 行）
**公式**：`x ← x + y`
**形状**：`[2048] + [2048]`
**MAC 推导**：2048 次加（**0.002 M**，可忽略）
**精度**：**FP32 + FP32 → FP32**
**坑**：残差是**唯一必须常驻 SRAM 的激活**（8 KB FP32）。24 层读写它 48 次，共 384 KB/token——小，但它是所有层的公共依赖，不能溢出到 DRAM。

### M6 MLP 合计与硬件含义

| 算子 | MAC | 占比 | 归属 |
|---|---:|---:|---|
| M1 RMSNorm | 0.006 M | 0.02% | VPU |
| M2 gate+up | 25.17 M | 66.7% | **矩阵引擎** |
| M3 SwiGLU | 0.012 M | 0.03% | VPU |
| M4 down | 12.58 M | 33.3% | **矩阵引擎** |
| M5 残差 | 0.002 M | 0.01% | VPU |
| **单层** | **37.75 M** | | |
| **24 层** | **906.0 M** | 47.8% of 1.90 G | |

两个需要注意的比例：

- 算力与带宽之比是 1:1。906 M MAC 对应 906 M 权重，即 934 MB（MXFP8, 1.031 B/param），算术强度 0.97 MAC/byte。
- 引擎吞吐下限：要在 310 GB/s 下不被算力拖住，引擎需要 `37.75 M MAC / (38.9 MB / 310 GB/s) = 302 GMAC/s`（≈ 604 GFLOPS，FP32 累加）。这个数字对全模型通用：M=1 的 MXFP8 GEMV，每 1 B/param 的带宽需要约 1 GMAC/s 的引擎吞吐。所以 310 GB/s 的 DRAM 配 300 GMAC/s 的引擎刚好平衡，引擎再快也没有用，除非上投机解码把 M 拉大。

---

## 6.4 层型 D：MTP（1 层）

MTP 是一个 60.8 M 参数的小模型：两路归一、一个 4096→2048 的融合、一个完整的全注意力层（含自己的 MLP）。它不在 `transformers` 的模型定义里——权重在 checkpoint 里，但加载时被 `^mtp.*` 静默丢弃。

它的用途只有一个：把 decode 的 M=1 GEMV 变成 M=γ+1 的 GEMM，用权重复用绕开内存墙。但草稿阶段自己要跑一遍 248K 词表的 `lm_head`，这一项会吃掉大部分收益（见 D6 的账）。

```
h_t   [2048] ──► pre_fc_norm_hidden ──┐
                                      ├─ cat ──► [4096] ──► fc [2048,4096] ──► [2048]
e_{t+1}[2048] ──► pre_fc_norm_embedding ┘                                        │
                                                                                 ▼
                                              layers.0（完整全注意力层：q/k/v/o + MLP）
                                                                                 │
                                                            mtp.norm ──► 共享 lm_head ──► 草稿 logits
```

### D1 pre_fc_norm_hidden
**作用**：归一化主干最后一层的隐状态，把 MTP 的输入尺度和主干对齐。
**调用**：MTP 模块（不在 `modeling_qwen3_5.py` 内，需自行实现）→ `Qwen3_5RMSNorm` 同构
**公式**：`h' = h · rsqrt(mean(h²)+1e-6) · (1+w)`，w 形状 `[2048]`（zero-centered）
**形状**：`[2048] → [2048]`
**MAC 推导**：6.1 K MAC
**精度**：FP32 归一，FP16 输出
**坑**：**也是 `1+w`**（`pre_fc_norm_*` 与主干同族）。用普通 RMSNorm 复现会整体偏一个常数倍，草稿接受率会掉到噪声水平而不报错。

### D2 pre_fc_norm_embedding + embedding 查表
**作用**：把“主干刚预测出的那个 token”的 embedding 归一，作为 MTP 的第二个输入（这是 MTP 能看到 t+1 的原因）。
**调用**：`embed_tokens(next_token_id)`（查表，与 lm_head 共享权重）→ `pre_fc_norm_embedding`
**公式**：`e' = Norm(E[id]) · rsqrt(...) · (1+w)`
**形状**：查表 `[248320, 2048]` 取 1 行 → `[2048]`；norm 权重 `[2048]`
**MAC 推导**：查表 0 MAC（纯访存 2048×1.031 B = **2.1 KB**）；norm 6.1 K MAC
**精度**：FP16（embedding 表本身可能就是 MXFP8，需反量化）
**坑**：
1. `mtp_use_dedicated_embeddings=false` → **必须复用主干的 `embed_tokens`**。如果自研实现另建一张表，等于白扔 508 M 参数的共享收益。
2. 查表是**跨 token 串行依赖**：`e_{t+1}` 依赖 `token_{t+1}`，而 `token_{t+1}` 是上一步采样出来的。**草稿的 γ 个 token 无法并行生成**——这是 MTP 的固有延迟下限。
3. 2.1 KB 的随机行读 = 一次 DRAM 事务，延迟 ~100 ns；在 6 ms 的 token 预算里可忽略，但在 γ 次串联后要计入。

### D3 concat + mtp.fc
**作用**：把“主干语义”和“下一 token 的身份”融成 2048 维，喂给下面那层注意力。
**调用**：`mtp.fc`（`nn.Linear(4096, 2048, bias=False)`）→ `F.linear`
**公式**：`x = W_fc · [h' ‖ e']`，`W_fc ∈ R^{2048×4096}`
**形状**：`[4096] → [2048]`；权重 `[2048, 4096]`
**MAC 推导**：

```
2048 × 4096 = 8,388,608 = 8.39 M
```
**精度**：输入 FP16 / MXFP8，FP32 累加，输出 FP16
**坑**：
1. **拼接顺序必须是 `[h ‖ e]`**（h 在前）。顺序反了不报错、形状也对，但等价于把训练好的 `W_fc` 左右两半对调，输出是噪声。移植时的头号静默 bug。
2. 4096 = 2×2048，K 维只有 4096，一次 tile 装得下；`N=2048` 偏小，引擎效率一般（建议与 D1/D2 的归约融合，或干脆和 D4 的 `input_layernorm` 合并成一个 kernel）。
3. 两个 norm 各自 2048 维、各一次归约 → **两次串行归约 + 一次 concat**，在 8.39 M MAC 面前延迟占比很高（两次 11 级归约 ≈ 70 拍）。可以并行做（它们互不依赖），硬件上应发到两条 VPU lane。

### D4 mtp.layers.0（一个完整的全注意力层）
**作用**：在 MTP 自己的 2048 维空间里做一次完整的 token mixing + MLP，产出“t+2 的隐状态”。
**调用**：与 `Qwen3_5DecoderLayer(block_type="full_attention")` **逐算子相同**：`input_layernorm` → `self_attn`（q_proj 双宽含 gate / k_proj / v_proj / q_norm / k_norm / partial RoPE / KV cache / softmax / sigmoid gate / o_proj）→ 残差 → `post_attention_layernorm` → `Qwen3_5MLP` → 残差
**公式**：见 6.2 B②–B⑧ + 6.3 M2–M5，完全一致
**形状**：

| 张量 | 形状 | 参数 |
|---|---|---:|
| `self_attn.q_proj` | `[4096, 2048]`（8 head ×256 ×2，含门控） | 8.39 M |
| `self_attn.k_proj` | `[512, 2048]` | 1.05 M |
| `self_attn.v_proj` | `[512, 2048]` | 1.05 M |
| `self_attn.o_proj` | `[2048, 2048]` | 4.19 M |
| `self_attn.q_norm` / `k_norm` | `[256]` ×2 | 512 |
| `mlp.gate_proj` / `up_proj` | `[6144, 2048]` ×2 | 25.17 M |
| `mlp.down_proj` | `[2048, 6144]` | 12.58 M |
| `input_layernorm` / `post_attention_layernorm` | `[2048]` ×2 | 4,096 |

**MAC 推导**：

```
q_proj(含gate)        2048 × 4096 =  8.39 M
k/v proj              2 × 512 × 2048 =  2.10 M
q/k norm              12 × 6.1 K   =  0.001 M
QKᵀ + AV              2 × 8 × 256 × S = 4096·S
gate + o_proj         0.002 + 4.19 = 4.19 M
MLP                   37.75 M
────────────────────────────────────────
合计（不含 S 项）      52.43 M
```
**精度**：与主干 attention 层完全一致（FP32 累加、FP16 激活、FP32 KV cache 写入前归一）
**坑**：
1. **它有自己的一套 KV cache**。q/k/v 投影是独立的，所以**不能复用主干的 KV**：每接受一个 token，MTP 层要多存 2×256×2 B = **1 KB/token**（和主干每个 attention 层同量级）。S=4096 时额外 4 MB。这意味着 MTP 层必须在**每个历史位置**都跑过一次（训练时确实如此），推理时要在验证 pass 里顺带把它的 K/V 写进去。
2. 它也有 **partial RoPE + mRoPE**，position_ids 与主干同源。别给它单独算一套 pos。
3. 它也有 **q_proj 双宽 + sigmoid 输出门**（`attn_output_gate=true` 是全局配置），漏掉门控会让草稿质量明显下降。
4. 这一层的 52.43 M 是 **M 个 token 一次算**（verify 时 M=γ+1），权重只读一遍；但 DeltaNet 那 18 层不在这里，MTP 只有 1 层全注意力，**没有递推**——这是它便宜的原因。

### D5 mtp.norm + 共享 lm_head
**作用**：归一后投影到词表，得到草稿分布。
**调用**：`mtp.norm`（`Qwen3_5RMSNorm`，`[2048]`）→ 主干的 `lm_head`（`nn.Linear(2048, 248320, bias=False)`，`_tied_weights_keys` 绑定 `embed_tokens.weight`）
**公式**：`logits = W_emb · Norm(x)`, `W_emb ∈ R^{248320×2048}`
**形状**：`[2048] → [248320]`
**MAC 推导**：

```
mtp.norm        6.1 K
lm_head         248320 × 2048 = 508.56 M      ← 是 fc 的 60 倍
```
**精度**：FP32 logits（采样需要）
**坑**：MTP 没有自己的 `lm_head`。权重清单里 `mtp.*` 只有 15 个张量（4 个 norm/fc 加 11 个 layer），没有任何 lm_head，它必须复用绑定的 `embed_tokens`。这带来一个经济学问题，见 D6。

### D6 投机解码的数学

**记号**：γ = 每步草稿 token 数，α = 单 token 接受率（i.i.d. 假设）。

**期望接受数**：
```
E[accepted] = Σ_{k=1}^{γ} Π_{i=1}^{k} α_i  =  α(1−α^γ)/(1−α)        （i.i.d.）
E[emitted]  = 1 + E[accepted] = (1 − α^{γ+1})/(1 − α)               （+1 是拒绝点的 bonus token）
```
**权重复用倍数**（理想上界，γ→∞、草稿零成本）：
```
lim_{γ→∞} E[emitted] = 1/(1−α)          α=0.7 → 3.33×
```
注意这一行是理论上界：它是 γ→∞ 且草稿零成本时的渐近值，不是单层 MTP 的实际加速比。

**有限 γ 的真实值**：

| γ | E[emitted] (α=0.7) | 权重复用 |
|---:|---:|---:|
| 1 | 1.70 | 1.70× |
| 2 | 2.19 | 2.19× |
| 3 | 2.53 | 2.53× |
| 5 | 2.94 | 2.94× |
| 8 | 3.20 | 3.20× |
| ∞ | 3.33 | 3.33× |

**把草稿成本算进去**（带宽口径，310 GB/s，MXFP8）：
```
基线：       B_tok = 1.941 GB                          → 6.26 ms → 160 tok/s
每步：       B_step = 1.941 + γ·d_draft  GB
吞吐：       T = E[emitted] · 310 / B_step   tok/s
加速比：     S = E[emitted] · 1.941 / B_step
```
| 草稿头 | `d_draft` | γ=1 | γ=2 | γ=3 | γ=5 |
|---|---:|---:|---:|---:|---:|
| 共享 248K lm_head（现实） | 62.7+524.3 = 587 MB | 1.31× | 1.37× | 1.33× | 1.13× |
| 剪枝到 8K 词表（推荐） | 62.7+17.3 = 80 MB | 1.63× | 2.02× | 2.25× | 2.44× |
| 草稿免费（理想上界） | 0 | 1.70× | 2.19× | 2.53× | 2.94× |

这张表最重要的结论是：`mtp.fc` 只有 8.39 M，但每产出一个草稿 token 都要跑一遍 508.56 M 的 `lm_head`（524 MB）。在 2B 模型上，`lm_head` 占单 token 带宽的 27%，所以"草稿很便宜"的直觉是错的：γ=2 时草稿要吃掉 60% 的额外带宽，净收益只有 1.37×，而不是 2.2×。

想让 MTP 兑现 2.3~2.5×，必须把草稿阶段的词表压到 4K~8K（用上一步 target 分布的 top-k 作为候选集），使 `lm_head` 的 524 MB 降到 17~35 MB。这一点比多叠几层 MTP 重要得多。

以上是纯带宽模型，未计 kernel 启动、CPU 采样、拒绝回滚开销。α 取 0.7 是假设值，实际首 token 接受率通常在 0.6~0.8，并随草稿位置衰减，严谨的做法是改用 `Πα_i` 的连乘形式。

**验证（verification）一侧**：一次 target 前向吃 M=γ+1 个 token，权重仍只读一遍，但**算力 ×(γ+1)**：
```
γ=2:  1.896 × 3 = 5.69 GMAC / 2.10 GB → 2.7 MAC/B → 引擎需 ≥ 840 GMAC/s 才不成为瓶颈
γ=3:  1.896 × 4 = 7.58 GMAC / 2.18 GB → 3.5 MAC/B → 引擎需 ≥ 1.08 TMAC/s
```
这是给矩阵引擎定吞吐的第二个约束（第一个是 302 GMAC/s 的 M=1 下限）。若产品规划了投机解码，引擎规格应按 1 TMAC/s 定，而不是 300 GMAC/s。

**接受/拒绝的分布正确性**：只有当验证用**拒绝采样 + 残差重采样**时，输出分布才严格等于 target：
```
u ~ U(0,1)
若 u ≤ min(1, p(x)/q(x))：接受草稿 x
否则：从归一化的 max(0, p−q) 中重采样
```
换成"草稿 == target argmax 才接受"的贪心验证，只在 T=0 时分布正确；T>0 时分布被扭曲（偏向草稿）。产品若开温度采样，必须用拒绝采样。

### D7 拒绝时的回滚要求（硬件必须支持）

| 状态 | 大小 | 回滚方式 | 代价 |
|---|---:|---|---|
| KV cache（6 层 attention + MTP 层） | 1 KB/token/层 | 尾指针截断（append-only 环形缓冲） | O(1)，免费 |
| DeltaNet `recurrent_state` | 18 × 1.05 MB = 18.9 MB | 验证前必须快照（递推不可截断、不可重放） | 37.8 MB 读+写 ≈ 0.12 ms（1.9% 步时） |
| DeltaNet `conv_state` | 18 × 36 KB = 0.66 MB | 同上，4-tap 窗口已被污染 | 1.3 MB，可忽略 |
| `mtp.layers.0` 的 KV | 1 KB/token | 尾指针截断 | 免费 |
| 草稿分布 `q` | γ × 248320 × 4B = γ MB | 保留到验证结束才能释放 | 3 MB @ γ=3 |
| RNG 状态 | 几 KB | 草稿与验证用两条独立流，拒绝不重放 | 免费 |

最贵的两项是 18.9 MB 的状态快照和 γ×1 MB 的草稿概率缓冲。前者是 2B 模型（18 层 DeltaNet）特有的：纯 Transformer 上投机解码几乎零回滚成本，在这个混合架构上不是。

还有一个容易漏掉的成本：DeltaNet 的递推逐 token 串行，M=γ+1 的验证不能把 18 层的递推批处理掉（每层要跑 γ+1 步串行）。它仍是带宽瓶颈的下属项（18 层 × 0.786 M MAC × (γ+1) 的串行延迟 ≈ 27~55 µs，对比 6.3 ms 的步时），但不要指望投机解码能加速递推本身。

### D8 MTP 总账

| 项 | 数值 |
|---|---:|
| 权重（15 个张量） | 60.83 M（fc 8.39 + layer 52.43 + 3 个 norm） |
| MXFP8 体积 | 62.7 MB = 基线 1.941 GB 的 3.2% |
| 单次前向 MAC（M=1，不含 S 项） | 60.83 M（fc 8.39 + 52.43 + 3×norm） |
| 额外 KV | 1 KB/token（S=4096 时 4 MB） |
| 额外状态回滚 | 18.9 MB 快照 |
| 净收益（共享 lm_head，α=0.7, γ=2） | ≈1.37× |
| 净收益（8K 剪枝草稿头，α=0.7, γ=3） | ≈2.25× |
| 加载 | `_keys_to_ignore_on_load_unexpected = [r"^mtp.*"]`（`modeling_qwen3_5.py:912`、`1668`），标准 `from_pretrained` 会静默丢弃全部 15 个张量，`modeling_qwen3_5.py` 里根本没有 MTP 类。要可用必须自己实现模块、草稿采样、验证和回滚。 |

---

---

# 第 7 章　视觉塔与输出头

视觉塔是标准 ViT：非因果注意力、LayerNorm、GELU 两矩阵 MLP。和文本主干（因果 + RMSNorm + SwiGLU）几乎每一项都相反。

它的特征是 GEMM 为主且 M 很大（M=784），算术强度约 799 MAC/B，是 decode（约 1 MAC/B）的 800 倍。同一个引擎在这里是两种用法。

一张 448² 图约 273.1 GMAC，相当于 144 个文本 prompt token；1024² 图是 2,093.8 GMAC，注意力的 `N²` 项把它推了上去。

输出头 `lm_head` 是最扁的 GEMV（N=248320, K=2048），占 decode 27% 的算力和 27% 的带宽，最终只产出一个整数。

---

## 7.1 视觉塔结构

视觉塔（`Qwen3_5VisionModel`，24 层 ViT）是一条纯前向、无缓存、无因果的编码流水线：一次前向把像素压成 196 个 2048 维 token，塞进文本序列的占位符位置。它的算力特征与 decode 相反——M 很大、K/N 固定、GEMM 占 88.9%，是全模型最适合矩阵引擎的部分。

```
pixel_values [N, 3·2·16·16=1536]      grid_thw = [t, h, w] = [T/2, H/16, W/16]
   │
V① patch_embed: Conv3d(3→1024, k=s=[2,16,16])          → [N, 1024]   ← 实为 GEMM，非滑窗卷积
   │  + pos_embed（2304×1024 表，4-tap 双线性 gather）    → [N, 1024]
V③ 2D RoPE（row/col 各 16 频，half-split，全 64 维旋转）  → cos/sin [N, 64]
   │
   ├── ×24  Qwen3_5VisionBlock ────────────────────────────────
   │     x = x + attn(LayerNorm(x))     V④ LN → V⑤ qkv(1024→3072) → V⑥ 非因果 MHA → V⑦ proj
   │     x = x + mlp (LayerNorm(x))     V④ LN → V⑧ fc1(1024→4096) → GELU(tanh) → fc2(4096→1024)
   │                                                          → [N, 1024]
   │
V⑨ merger: LayerNorm(1024) → view(N/4, 4096) → fc1(4096→4096) → GELU(erf) → fc2(4096→2048)
   │                                                          → [N/4, 2048]
V⑩ masked_scatter 到 input_ids == 248056 的位置               → [S, 2048]
```

**记号**（后文全部沿用）

| 符号 | 含义 | 448×448, T=2 时 |
|---|---|---:|
| `N` | patch token 数 = `(T/2)·(H/16)·(W/16)` | 784 |
| `M` | merge 后视觉 token 数 = `N/4` | 196 |
| `d` | 隐层 `hidden_size` | 1024 |
| `H_a` / `hd` | 头数 / 头维 | 16 / 64 |
| `I` | MLP 中间维 | 4096 |
| `S` | pos_embed 表边长 `√2304` | 48 |

**一个容易踩的前提**：patch token 在整条流水线里是**按 2×2 merge 块排列的（block-major），不是光栅序**。`get_vision_position_ids` 把 `hpos_ids.reshape(h//2, 2, w//2, 2).transpose(1,2)`，`get_vision_interpolation_indices_and_weights` 也用 `(block_row, block_col, in_row, in_col)` 解码——两者都假定输入顺序已是 block-major（由 image processor 保证）。这带来一个好处：merger 里 4 个 patch 是**连续 4 行**，`view(-1,4096)` 是零开销的连续拼接。若你自建预处理按光栅序喂入，必须自己补一次 shuffle，否则 pos id、RoPE、merge 分组会全部错位 。

**与文本主干的差异（逐项）**

| 项 | 文本主干（Qwen3.5 2B） | 视觉塔 |
|---|---|---|
| 归一化 | RMSNorm，zero-centered `1+w`，**无 bias** | **LayerNorm，带 γ/β，减均值** |
| 激活 | SiLU（SwiGLU 门控） | **GELU**；block 用 tanh 近似，merger 用 erf 精确 |
| MLP | 3 矩阵（gate/up/down） | **2 矩阵，无门控** |
| 注意力 | 因果 + GQA + 部分 RoPE(64/256) | **非因果 + 16 头全 MHA + 2D RoPE(64/64)** |
| bias | 全无 | **全有**（每个 Linear 都带 bias） |
| 位置编码 | mRoPE（t/h/w 三轴交错） | 学习式 `pos_embed` 插值 + 2D 轴向 RoPE |
| 序列组织 | 单条因果序列 + KV cache | **packed 变长段（`cu_seqlens`），无 cache** |
| qk-norm | 有 | **无** |
| dropout | 0 | 0（`attention_dropout=0.0`，`is_causal=False`） |
| deepstack | — | `deepstack_visual_indexes=[]` → **只取最后一层**，无多层注入 |

**权重清单与带宽**（实测 key，MXFP8 = 1 B/param）

| 参数 | 形状 | 参数量 | 字节 |
|---|---|---:|---:|
| `visual.patch_embed.proj.{w,b}` | [1024,3,2,16,16] / [1024] | 1,573,888 | 1.57 MB |
| `visual.pos_embed.weight` | [2304,1024] | 2,359,296 | 2.36 MB |
| `blocks.*.norm1/2.{w,b}` | [1024]×2 ×2 | 4,096 | 4 KB |
| `blocks.*.attn.qkv.{w,b}` | [3072,1024] / [3072] | 3,148,800 | 3.15 MB |
| `blocks.*.attn.proj.{w,b}` | [1024,1024] / [1024] | 1,049,600 | 1.05 MB |
| `blocks.*.mlp.linear_fc1.{w,b}` | [4096,1024] / [4096] | 4,198,400 | 4.20 MB |
| `blocks.*.mlp.linear_fc2.{w,b}` | [1024,4096] / [1024] | 4,195,328 | 4.20 MB |
| **单 block 合计** | | **12,596,224** | 12.60 MB |
| 24 blocks | | 302,309,376 | 302.3 MB |
| `merger.norm.{w,b}` | [1024]×2 | 2,048 | 2 KB |
| `merger.linear_fc1.{w,b}` | [4096,4096] / [4096] | 16,781,312 | 16.78 MB |
| `merger.linear_fc2.{w,b}` | [2048,4096] / [2048] | 8,390,656 | 8.39 MB |
| **视觉塔总计** | | **331,416,576** | **331.4 MB**（+3.1% E8M0 scale ≈ 342 MB） |

视觉塔权重是文本主干（1.94 GB 的 MXFP8 权重）的 17.6%，但每张图只读一遍，不是每 token 一遍。算术强度 `273 GMAC / 0.342 GB ≈ 799 MAC/B`，而 decode 只有 ~1 MAC/B。同一个引擎，两种差两个数量级的使用方式。

---

## 7.2 视觉塔逐算子拆解（V①–V⑩）

### V① Patch Embed（Conv3d → 隐式 GEMM）

**作用**：把每 `2×16×16` 像素块线性投影成 1 个 1024 维 token，同时完成 `T/2` 的时间下采样与 `16×16` 的空间下采样。

**调用**：`Qwen3_5VisionPatchEmbed.forward` → `nn.Conv3d(3, 1024, kernel_size=[2,16,16], stride=[2,16,16], bias=True)`

**公式**：
$$y_{n,o}=\sum_{c=0}^{2}\sum_{t=0}^{1}\sum_{i=0}^{15}\sum_{j=0}^{15} W_{o,c,t,i,j}\,x_{n,c,t,i,j}+b_o,\qquad n=0\ldots N-1,\ o=0\ldots 1023$$

`stride == kernel_size` 且无 padding，所以输出空间尺寸恒为 1×1×1——**这不是卷积，是 `[N,1536] × [1536,1024]` 的一次 GEMM**（im2col 退化为一维展平，无重叠、无边界处理）。

**形状**：
- 输入 `pixel_values`：`[N, C·tps·ps·ps] = [784, 1536]`，`forward` 内 `view(-1, 3, 2, 16, 16)`
- 权重 `[1024, 3, 2, 16, 16]` → 展平 `[1024, 1536]`
- 输出 `[N, 1024] = [784, 1024]`
- token 数：`N = (T/2)·(H/16)·(W/16)`；448×448、T=2 → `1 × 28 × 28 = 784`

**MAC 推导**：单 patch `1024 × 3 × 2 × 16 × 16 = 1,572,864`（≈1.573 M，含 bias 加 1024 次可忽略）；整图 `784 × 1,572,864 = 1,233,125,376 ≈ 1.233 GMAC`，占整图 **0.45%**。

**精度**：
- 输入端是归一化像素（连续值，动态范围小但 per-channel std 不同），K 方向 1536 = 48 个 MXFP8 block；一个 block 的 32 个元素会横跨 c/t/i/j 四个维度，块内幅度差约 2–3×，E4M3 + E8M0 共享 scale 完全够（E4M3 量程 ±448）。但 patch_embed 是唯一直接吃原始像素的算子，量化噪声无任何前置平滑；它只占 0.45% 算力，**若引擎有 FP16 入口，建议这层走 FP16**，否则 MXFP8 结果需与 bf16 基线做逐元素比对（预期 cos > 0.999）。
- 源码 `hidden_states.to(dtype=self.proj.weight.dtype)`：HF 里此处把像素 cast 到 bf16，注意别在你的参考实现里漏掉这一步（会影响比对基线）。

**坑**：
- 别按“卷积”去设计数据通路——没有 halo、没有滑窗复用，**每字节输入只用一次**，这是纯 GEMM，权重 1.57 MB 常驻即可。
- `T` 是**像素帧数**，`t = T/2` 是 patch 帧数。T=1（单帧图）时 processor 会把该帧复制成 2 帧，`t` 仍是 1；不要写成 `t=T`。
- 输出行序 = 输入行序 = block-major（见 7.1），patch_embed 不做任何重排。

---

### V② 位置编码（学习式表 + 4-tap 双线性插值）

**作用**：给每个 patch 加一个**绝对**位置向量。表是学出来的（`nn.Embedding`），但按图像实际网格做双线性重采样，所以支持任意分辨率。

**调用**：`get_vision_interpolation_indices_and_weights(...)` 预计算 → `self.pos_embed(interp_indices) * interp_weights[:, :, None]).sum(1)`

**公式**：表边长 `S = √2304 = 48`，`align_corners=True`，图像网格 `(h, w) = (H/16, W/16)`。对 patch `(r, c)`：

$$s_r = r\cdot\frac{S-1}{h-1},\quad s_c = c\cdot\frac{S-1}{w-1}$$
$$i_0=\lfloor s_r\rfloor,\ i_1=\min(i_0+1,S-1),\ \alpha=s_r-i_0;\qquad j_0,j_1,\beta\ \text{同理}$$
$$p_{r,c}=\sum_{a\in\{0,1\}}\sum_{b\in\{0,1\}}(1-\alpha)^{1-a}\alpha^{a}(1-\beta)^{1-b}\beta^{b}\;E\big[(i_0+a)\cdot S+(j_0+b)\big]$$

展开就是熟悉的 `(1−α)(1−β)E00 + (1−α)βE01 + α(1−β)E10 + αβE11`，4 个 tap、权重和为 1（`padding="border"` 时越界 tap 被 clamp 到边界，权重和仍为 1）。

**形状**：
- `interp_indices`：`[N, 4]` int64（784×4，25 KB）
- `interp_weights`：`[N, 4]` float32
- gather 结果 `[N, 4, 1024]` → 加权求和 → `[N, 1024]`
- 448²：`s_r = r·47/27 ≈ 1.741r`，**降采样** 28←48；1024²：`s_r = r·47/63 ≈ 0.746r`，**升采样** 64←48

**MAC 推导**：`N × 4 × 1024 = 3.21 M` 次乘加（784×4×1024），整图占比 **0.001%**——算力可忽略，但它是**访存型**算子。

**精度**：表本身可 MXFP8（2.36 MB，能整表进 L2）；4 个 tap 的加权和在 FP16 里做即可，插值权重 FP32。注意加权和**不能**在 MXFP8 累加器里做（4 项动态范围差 10× 以上）。

**坑**：
- **这不是 GEMM**：是 gather + 逐元素加权求和，矩阵引擎做不了（除非引擎支持 embedding-bag 类指令）。落点是 VPU/向量单元。
- 访存特征：每 patch 读 4 行 × 1024 × 1 B = 4 KB，784 patch 共 3.1 MB，但表只有 2.36 MB → **L2 命中率接近 100%**，真实 DRAM 流量≈表大小。别把它算成 3.1 MB/图的 DRAM 带宽。
- `align_corners=True` 是硬约束（`self.interpolation_align_corners = True`），换成 False 会整体偏移半格，位置编码全错但**不会报错**——这类 bug 只能靠数值比对发现。
- 该表只有 2304 项，`h` 或 `w` 超过 48 时是升采样（插值），不会越界；但 `h=1` 或 `w=1` 时 `size-1=0`，源码用 `clamp(min=1)` 兜住（除零保护），你的复现也要兜。

---

### V③ 2D 轴向 RoPE（row/col 两套频率，half-split）

**作用**：给 q/k 注入**相对**位置信息。视觉塔是 2D 的：把 head_dim 劈成「行频率」和「列频率」两半，分别用 patch 的行号、列号旋转。

**调用**：`Qwen3_5VisionRotaryEmbedding.forward`（`rope_type="axial"`）→ `apply_rotary_pos_emb_vision` → 内部 `rotate_half`（half-split：`cat(-x[32:], x[:32])`）

**公式**：`head_dim=64`，`spatial_dim = 64/2 = 32`，`inv_freq` 长度 16：

$$\text{invf}_k=\text{base}^{-2k/32}=\text{base}^{-k/16},\quad k=0\ldots15$$
$$\theta^{h}_k=r\cdot\text{invf}_k,\qquad \theta^{w}_k=c\cdot\text{invf}_k$$
$$\text{cos}=\big[\cos\theta^{h}_{0:16},\ \cos\theta^{w}_{0:16},\ \cos\theta^{h}_{0:16},\ \cos\theta^{w}_{0:16}\big]\in\mathbb{R}^{64}\quad(\text{sin 同理})$$

`recomposition_frequencies` 就是 `cat([h,w])` 再 `cat` 自己一遍，把 32 维频率铺满 64 维。作用到 q（`[N,16,64]`）上：

$$q'_d=q_d\cos_d-q_{d+32}\sin_d,\qquad q'_{d+32}=q_{d+32}\cos_d+q_d\sin_d,\quad d=0\ldots31$$

即 `(d, d+32)` 成对旋转，角度 `θ_d`：`d<16` 用行频、`16≤d<32` 用列频，后 32 维重复同一套角度。这是标准 half-split（GPT-NeoX 式），**不是** interleaved（GPT-J 式 `(2i,2i+1)` 配对）。

**形状**：`position_ids [N,2]`（行 0 = h，行 1 = w）→ `freqs [N,2,16]` → `cos/sin [N,64]` → `unsqueeze(-2)` 后 `[N,1,64]` 广播到 16 头。

**MAC 推导**：每 (token, head, dim) 6 次浮点运算（4 乘 2 加），q 与 k 各一遍：
`784 × 16 × 64 × 6 × 2 ≈ 9.63 M ops/block`，24 层 ≈ **231 M ops**（≈0.08% 算力）。但附带一次 `q,k → FP32 → 回原 dtype` 的往返（`apply_rotary_pos_emb_vision` 里显式 `.float()`），即每 block 3.2 M 次转换，是**访存开销**而非算力开销。

**与文本 mRoPE 的差异**

| 项 | 文本 mRoPE | 视觉 2D RoPE |
|---|---|---|
| 轴数 | 3（t/h/w），`mrope_section=[11,11,10]` 交错覆写 | 2（row/col），前后两半各 16 频 |
| 覆盖维度 | 仅前 64/256 维（`partial_rotary_factor=0.25`），其余透传 | **全 64/64 维**，无 partial |
| 频率拼接 | `freqs_thw[..., idx] = freq[dim, ..., idx]`，`idx = slice(offset, len, 3)` | `cat([h,w])` 再整体复制一遍 |
| base | `1e7` | 未在核实清单内（Qwen2/2.5-VL 视觉塔为 1e4；本文公式按 base 通用写，换 base 只改频率表，**形状与 MAC 不变**） |
| scaling | 可能带 yarn 缩放 | `attention_scaling = 1.0`（恒等） |
| 位置语义 | 绝对 token 位置（含图像 token 的 3D 展开） | **patch 网格坐标**，与图像分辨率解耦 |

**坑**：
- 频率只有 16 个、角度按 `base^{-k/16}` 递减，最高频波长 `2π`（k=0，周期 1 个 patch），最低频 `2π·base^{15/16}`。48/64 格网格内不会出现混叠，但**别把 `position_ids` 当成像素坐标**（它是 patch 索引 0..h-1）。
- `cos/sin` 是 **FP32**（`forward` 不 cast 回 x.dtype），量化到 MXFP8 前请先确认引擎的 rotary 单元能吃 FP32 角度表，否则精度会掉在 `sin/cos` 上而不是 GEMM 上。
- 只作用于 q、k，不作用于 v；且**因为非因果**，RoPE 在这里只影响 attention score 的相对偏移，不承担“防未来信息泄漏”的职责。

---

### V④–V⑧ 一个 Vision Block（×24）

单 block 是 **pre-norm 双残差**结构（与文本主干的 post-norm/zero-centered RMSNorm 不同）：

```
x = x + proj( attn( qkv( LayerNorm1(x) ) ) )     # V④→V⑤→V⑥→V⑦
x = x + fc2( gelu( fc1( LayerNorm2(x) ) ) )      # V④→V⑧
```

#### V④ LayerNorm（带 bias，非 RMSNorm）

**作用**：对每个 token 的 1024 维做零均值单位方差归一化，再仿射。

**调用**：`nn.LayerNorm(1024, eps=1e-6)`（`norm1`、`norm2` 各一份，`elementwise_affine=True`）

**公式**：
$$\mu=\frac1d\sum_i x_i,\quad \sigma^2=\frac1d\sum_i (x_i-\mu)^2,\quad y_i=\gamma_i\frac{x_i-\mu}{\sqrt{\sigma^2+\epsilon}}+\beta_i$$

对比文本主干的 zero-centered RMSNorm：
$$\text{RMS}(x)=\sqrt{\tfrac1d\textstyle\sum_i x_i^2+\epsilon},\quad y_i=(1+w_i)\frac{x_i}{\text{RMS}(x)}$$

**形状**：`[N,1024] → [N,1024]`；γ、β 各 `[1024]`。每 block 两份，共 4 KB 参数。

**MAC 推导**：本质是归约，不是 MAC。每 token 约 `4d` 次运算（求和、平方和、归一化、仿射）= 4×1024 ≈ 4 k ops；每 block `2 × 784 × 4k ≈ 6.4 M ops`，24 层 ≈ **154 M ops**（0.06%）。但它要求 **2 次全行归约 + 1 次重读**，延迟不低。

**精度**：必须 FP32 统计量（`μ`、`σ²`），归一化与仿射可 FP16；输出要进 GEMM，需再量化成 MXFP8（per-32 block E8M0）。`eps=1e-6` 很小，若统计量用 FP16 会在方差小的通道上炸掉 。

**坑**：
- **带 bias**：文本主干 RMSNorm 是 `1+w`、无 β；视觉 LN 是 `γ, β` 都学。移植时若复用文本的 norm 单元，会**少一组 β、多一个 `1+`**，数值立刻错（且不报错）。
- **要减均值**：RMSNorm 只需 `Σx²` 一遍归约，LN 需要 `Σx` 和 `Σ(x−μ)²`（或 Welford）。硬件上多一遍 pass，别按 RMSNorm 的周期数排流水。
- LN 输出是 GEMM 的 A 操作数 → **每个 LN 后面都紧跟一次 MXFP8 量化**（求 block amax、乘 E8M0 倒数、舍入）。每 token 每 block 有 2 处这样的量化点，加上 q/k/P/attn_out 共 5–6 处，整图约 `5.5 × 784 × 1024 × 24 ≈ 106 M` 元素要过一遍量化流水，是 1% 量级的向量开销，排 pipeline 时别漏。

#### V⑤ QKV 融合投影

**作用**：一次 GEMM 同时算出 Q、K、V。

**调用**：`self.qkv = nn.Linear(1024, 3072, bias=True)`；`forward` 里 `.reshape(N,3,16,-1).permute(1,0,2,3).unbind(0)`

**公式**：`[q;k;v] = x W_qkv^T + b`，`W_qkv ∈ R^{3072×1024}`

**形状**：`[N,1024] × [1024,3072] → [N,3072]`，再拆成 3×`[N,16,64]`。**权重行分段**：`W[0:1024]→Q`、`W[1024:2048]→K`、`W[2048:3072]→V`，每段内部再按 16 头 × 64 维切。

**MAC 推导**：`N × 1024 × 3072 = 784 × 3,145,728 = 2,466.3 MMAC`（每 block）；占单 block 线性 MAC 的 **25.0%**。bias 只加 3072 次。

**精度**：标准 GEMM，A/B 双 MXFP8、FP32 累加、FP16 输出。输入来自 LN，分布接近 N(0,1)，per-32 block 缩放友好。ViT 的 LN 输出偶有通道离群值（幅度 20–30），一个 32 通道 block 里若混进一个离群通道，其余 31 个通道的有效位数会被压低——上线前请统计每层 LN 输出的 per-block amax 分布。

**坑**：
- `attn.qkv` 有 **bias**（文本主干无 bias）；融合 GEMM 的 epilogue 要支持 3072 维 bias 广播，否则得回 VPU 再加一遍（多一次 784×3072 的读写）。
- `reshape(N,3,H,hd)` 的切分顺序是 **Q 整段 → K 整段 → V 整段**，不是 per-head 交错。若按 head 交错去解析权重，Q/K/V 会互相串。
- 16 个 K 头 = 16 个 Q 头，**没有 GQA**（`num_key_value_groups=1` 只是给 eager 实现兜底的字段），也没有 qk-norm。

#### V⑥ 非因果全头注意力（Vision Attention）

**作用**：784 个 patch 两两全连接（段内），16 头独立，无因果掩码、无位置掩码。

**调用**：`Qwen3_5VisionAttention.forward` → `ALL_ATTENTION_FUNCTIONS.get_interface(config._attn_implementation, eager_attention_forward)`；flash 路径传 `cu_seq_lens_q/k`、`max_length_q/k`、`is_causal=False`；非 flash 路径按 `cu_seqlens` 逐段 `torch.split` 后循环。

**公式**：
$$S=\frac{QK^\top}{\sqrt{hd}}=\frac{QK^\top}{8}\quad(\text{scaling}=hd^{-0.5}=1/8)$$
$$P=\text{softmax}_{\text{row}}(S)\quad(\text{无 mask，全 784 列参与})$$
$$O=PV,\qquad \text{out}=O\,W_{proj}^\top+b_{proj}$$

**形状**：`Q,K,V [N,16,64]` → 接口内部 `[1,16,N,64]`（batch 维恒为 1，多个图靠 `cu_seqlens` 分段，不靠 batch）。
- `cu_seqlens`：`int32`，长度 = 段数+1。`get_vision_cu_seqlens` 默认 `merge_temporal=False`，即 `seqlens = repeat_interleave(h*w, t)` → **每一帧是一个独立段**。
- 单图 T=2、448²：`cu_seqlens = [0, 784]`，`max_seqlen = 784`，1 段。
- 视频 T=8、448²：`cu_seqlens = [0,784,1568,2352,3136]`，**4 段**，`max_seqlen = 784`（不是 3136）。
- 两张图同批：`[0,784,1568]`，跨图不互相 attend。

**MAC 推导**：`QKᵀ` = `N²·hd·H_a = 784²×1024 = 629.4 MMAC`；`PV` 同量 = 629.4 MMAC；合计 `2N²d = 1,258.8 MMAC/block`（单段）。24 层 = **30.21 GMAC/图**，占整图 **11.1%**、占单 block 线性部分的 **12.8%**。softmax 另算：`16×784² ≈ 9.83 M` 个元素 × 约 5 ops = 49 M ops/block，24 层 ≈ 1.18 G ops（非 MAC，但吃向量单元）。

**精度**：
- Q/K 可 MXFP8：`scaling=1/8` 可**折进 Q 的 per-block E8M0 scale**，不用单独一遍缩放，免费。
- `QKᵀ` 必须 FP32 累加（K=64 很短，FP16 累加其实也够，但 score 要进 softmax，FP32 更稳）。
- softmax 必须 **FP32 + 行最大值**（无 mask 也要减 max，784 列里 score 幅度可达 ±30）。
- `PV` 的 P 是 `[0,1]` 概率：**这是全视觉塔量化风险最高的一点**。P 的绝大多数质量集中在 `[0, 0.05]`，E4M3 最小正规数 2⁻⁶≈0.0156，靠 per-32 block scale 才不至于全变 0；相对误差 ~2⁻⁴≈6%，但 attention 输出是 784 个 V 行的加权平均，误差会平均掉。**必须**用 bf16 基线做端到端比对（建议指标：每层 attention 输出的相对 L2 误差 < 1%，最终 196 个 token 的 cos > 0.999）。

**坑**：
- **非因果**：`is_causal=False` 写死在 `__init__` 和调用两侧。任何“顺手复用文本 attention kernel”的做法都会带上 causal mask，结果不是慢，是**错**（每个 patch 只看前一半 patch）。
- **packed 变长**：`cu_seqlens` 默认**按帧切段**（`merge_temporal=False`），不是按图、也不是按整批。若按整批 `[0, ΣN]` 做一次 dense attention：多图之间串味 + 视频跨帧串味，且 MAC 从 `Σ n_i²` 变成 `(Σ n_i)²`。以 4 帧 448² 视频为例，正确 120.9 GMAC，错误 483.4 GMAC（**4×**），还全错。
- `max_seqlen` 是 tile 调度的依据（FA 语义），不是 `cu_seqlens[-1]`；批内混入 1024² 与 448² 时，`max_seqlen` 取大者，小图段会浪费 tile。
- score 矩阵若整体物化：`16 × 784² × 2 B = 19.7 MB`（448²）/ **537 MB**（1024²，4096 token）。1024² 必须分块 + online softmax，别指望 L2。
- 没有 attention mask（`attention_mask=None`），所以**不需要 padding token**；若你的引擎实现要求矩形 batch，pad 出来的位置会真实参与 softmax，必须靠 `cu_seqlens` 而不是 mask 来排除。

#### V⑦ 输出投影 proj

**作用**：多头拼接后回到隐层维度。

**调用**：`self.proj = nn.Linear(1024, 1024)`（bias=True）

**公式**：`y = \text{concat}_{h}(O_h)\,W_{proj}^\top + b`

**形状**：`[N, 16×64=1024] → [N,1024]`；`attn_output.reshape(seq_length, -1)` 保证 concat 顺序是 `head0[0:64], head1[0:64], …`。

**MAC 推导**：`784 × 1024 × 1024 = 822.1 MMAC/block`，占单 block 线性 **8.3%**；24 层 19.7 GMAC。

**精度**：标准 GEMM，可全 MXFP8。输出直接进残差（FP16/FP32），**不要**量化回 MXFP8 再相加。

**坑**：`reshape(N,-1)` 隐含 `contiguous()`；若上游 attention 输出是 `[1,16,N,64]` 的转置视图，这里的 layout 转换是一次 1.6 MB 的搬运，别当免费。

#### V⑧ MLP（两矩阵、无门控、GELU-tanh）

**作用**：逐 token 的通道混合，`1024 → 4096 → 1024`。

**调用**：`Qwen3_5VisionMLP.forward` → `linear_fc2(act_fn(linear_fc1(x)))`，`act_fn = ACT2FN["gelu_pytorch_tanh"]`

**公式**：
$$h=\text{GELU}_{\tanh}(xW_1^\top+b_1),\qquad y=hW_2^\top+b_2$$
$$\text{GELU}_{\tanh}(z)=0.5z\Big(1+\tanh\big(\sqrt{2/\pi}\,(z+0.044715z^3)\big)\Big)$$

**形状**：`[N,1024] → [N,4096] → [N,1024]`；`W1 [4096,1024]`、`W2 [1024,4096]`，bias 各 4096/1024。

**MAC 推导**：`fc1 = 784×1024×4096 = 3,288.3 MMAC`，`fc2 = 784×4096×1024 = 3,288.3 MMAC`，合计 **6,576.6 MMAC/block = 单 block 线性的 66.7%**（这也是全视觉塔最大的一块）。24 层 = 157.8 GMAC。GELU 本身：`784×4096 = 3.21 M` 元素 × 约 10 ops ≈ 32 M ops/block（tanh 走 SFU，吞吐约为 FMA 的 1/4，实际耗时占比高于 ops 占比，24 层约 0.3–0.8% 时间）。

**精度**：
- 两个 GEMM 都是“胖”GEMM（M=784, K/N=1024/4096），量化友好。
- **GELU 的输入是 fc1 的 FP32 输出**；GELU 之后必须重新量化成 MXFP8 才能喂 fc2。也就是说 `fp32_acc → +bias → tanh-gelu(fp32) → amax/scale → E4M3`，这条链要在 FP32 里走完，不能在 FP16 里做 tanh 再量化（tanh 在 |z|>4 时饱和，FP16 输入误差会被放大到指数项）。
- 输入 `z` 的分布：fc1 输出 std 约 0.5–1，`0.044715z³` 项在 |z|>3 时主导，tanh 饱和区大量出现 → **近似误差主要集中在 |z|∈[2,4]**，这是 tanh 近似与 erf 精确解的差异区；两种实现的逐元素差可达 1e-3 量级（相对），但只影响约 5% 的元素。

**坑**：
- **无门控**：只有 2 个矩阵。文本主干是 SwiGLU 3 矩阵，按 3 矩阵去算会**多算 50%**（也会多读 50% 权重）。
- **GELU 而非 SiLU**；且 block 用 **tanh 近似**（`gelu_pytorch_tanh`），而 merger 用 `nn.GELU()` 默认 **erf 精确**（见 V⑨）。同一份代码里两种 GELU，硬件上要么都实现，要么其中一处会引入系统性偏差。
- fc1 输出 4096 维中间张量 = `784×4096×2 B = 6.4 MB`（448²）/ 33.6 MB（1024²），放不进小 SRAM 时要落 L2；融合“fc1→GELU→量化→fc2”的 tiling 能省掉这次往返，是引擎侧最值得做的一个 fusion。

**两处残差**：`x = x + attn_out`、`x = x + mlp_out`，均为 FP16/FP32 逐元素加，`[N,1024]`，无缩放因子（不是 LayerScale，也不是 residual scaling）。24 层累加后残差流幅度增长约 `√24 ≈ 5×`，**残差必须保持 FP16 以上**，中途量化到 MXFP8 会让 24 层误差累积到不可用 。

---

### V⑨ Patch Merger（2×2 拼 token，N → N/4）

**作用**：把空间上相邻的 2×2 = 4 个 patch 拼成 4096 维，再压到 2048 维，同时 token 数变为 1/4。这是视觉塔与文本主干的**维度接口**（1024 → 2048）。

**调用**：`Qwen3_5VisionPatchMerger(use_postshuffle_norm=False)` →
`norm(x).view(-1,4096)` → `linear_fc1` → `nn.GELU()`（erf）→ `linear_fc2`

**公式**（源码顺序，关键）：
$$u_i=\text{LayerNorm}_{1024}(x_i)\quad(\text{逐 patch，在 1024 维上做})$$
$$z_k=[u_{4k};u_{4k+1};u_{4k+2};u_{4k+3}]\in\mathbb{R}^{4096}$$
$$y_k=W_2\,\text{GELU}_{\text{erf}}(W_1 z_k+b_1)+b_2,\qquad W_1\in\mathbb{R}^{4096\times4096},W_2\in\mathbb{R}^{2048\times4096}$$

`use_postshuffle_norm=False` 的含义：`self.norm` 的宽度是 `config.hidden_size=1024`（不是 4096），`forward` 里 `self.norm(x)` 直接吃 `[N,1024]`，**先 norm 再 view**。若为 `True`，则是 `norm(x.view(-1,4096))`——先拼再 norm。两者统计量完全不同（1024 个元素的 μ/σ vs 4096 个元素的 μ/σ），**不可互换**，且权重形状不同（`[1024]` vs `[4096]`），换错会在 load 时报 shape mismatch（这点还算幸运）。

**形状**：`[784,1024] → LN → [784,1024] → view → [196,4096] → fc1 → [196,4096] → fc2 → [196,2048]`

**MAC 推导**（按真实 M=196）：
- `fc1`：`196 × 4096 × 4096 = 3,288.3 MMAC`
- `fc2`：`196 × 4096 × 2048 = 1,644.2 MMAC`
- 合计 `196 × (16,777,216 + 8,388,608) = 196 × 25,165,824 = 4,932.5 MMAC ≈ **4.93 GMAC**`（占整图 1.8%）
- LN：`784 × 4×1024 ≈ 3.2 M ops`（可忽略）
- GELU(erf)：`196×4096 = 0.80 M` 元素

**精度**：`fc1`/`fc2` 可 MXFP8；但**这是视觉塔唯一直接决定注入文本序列数值的算子**，它的误差会经 196 个 token 传播到后续所有文本层。权重只有 25.2 M（占视觉 7.6%），建议**这两层保留 FP16 权重**，或至少保证注入后与 bf16 基线的 cos > 0.9999。

**坑**：
- **常见 4× 高估**：按 `784 × (4096×4096 + 4096×2048) = 19.7 GMAC` 算是错的——`x.view(-1,4096)` 已经把 4 个 patch 合成一行，GEMM 的 **M 是 196 不是 784**。真实值 4.93 GMAC。
- **顺序**：先 LN（1024 维）→ 再 view 拼 4096。别写成“先 concat 再 LN”。
- **拼接是免费的**：因为 token 是 block-major，`[4k..4k+3]` 是**连续 4 行**，`view(-1,4096)` 就是一次连续内存解释，零 shuffle、零 DMA。若你的预处理是光栅序，这里才需要一次真正的 2×2 交织 gather（4 段 2 KB 的 strided copy），成本与“免费”差两个数量级 。
- **GELU 变体**：这里 `nn.GELU()` 是 **erf 精确版**，不是 V⑧ 的 tanh 近似。硬件 SFU 通常只有 tanh，erf 需要多项式近似（建议 Abramowitz-Stegun 7.1.26，绝对误差 1.5e-7）——只 0.8 M 元素，代价可接受，但**不能**直接用 tanh 版顶替（在 |z|<1 区域差 ~1e-3）。
- 输出 2048 维正好等于文本 `hidden_size`，所以注入不需要额外投影；`BaseModelOutputWithPooling.pooler_output` 就是这 196×2048，而 `last_hidden_state` 是 merge 前的 784×1024（别拿错）。

---

### V⑩ 注入文本序列

**作用**：把 196 个 2048 维视觉 token 写到文本 embedding 里 `image_token_id=248056` 占位的位置。

**调用**：`Qwen3_5ForConditionalGeneration.forward` → `get_placeholder_mask` → `inputs_embeds.masked_scatter(image_mask, image_embeds)`

**公式**：
$$\text{mask}_s=\mathbb{1}[\text{input\_ids}_s=248056],\qquad E'=\text{scatter}(E,\text{mask},F),\ \ F\in\mathbb{R}^{196\times2048}$$

**形状**：`inputs_embeds [S,2048]`，`mask [S,1]`（bool），`image_embeds [196,2048]` → `[S,2048]`。占位符个数必须**严格等于** `196`，否则 `torch_compilable_check` 抛错（`n_image_tokens × 2048 == image_features.numel()`）。

**MAC 推导**：0（纯数据搬运）。代价是 `196×2048×2 B = 803 KB` 的写 + 读。

**精度**：视觉塔输出是 FP16/bf16，注入前 `.to(inputs_embeds.dtype)`；**不要**在这里量化成 MXFP8，文本 embedding 表和视觉 token 会进同一个残差流，混合精度会引入 per-token 的尺度跳变。

**坑**：
- `masked_scatter` 是**按行序**填充：视觉 token 的顺序必须与 `input_ids` 里 248056 的出现顺序一致。多图时按图在 prompt 中出现的先后拼接（`torch.cat(pooler_output, dim=0)`），不要按 batch 维。
- 图像 token 数由 processor 按 `(h/32)×(w/32)` 写入占位符，**与视觉塔实际输出必须一致**；分辨率不是 32 的倍数时 processor 会先 pad，pad 出来的 patch 也参与 attention（无 mask），这是精度损失点，不是错误 。
- 注入后这 196 个 token 就是普通文本 token：参与 mRoPE（位置 id 由 `get_rope_index` 按 3D 网格展开）、进 KV cache、被后续 token attend。视觉塔本身**不进 cache、不重复计算**——图只编码一次。

---

## 7.3 视觉塔的成本

### 单图 448×448、T=2（t=1，N=784，M=196）

| 阶段 | 计算式 | MAC | 占比 |
|---|---|---:|---:|
| V① patch_embed | `784 × 1,572,864` | 1.233 G | 0.45% |
| V② pos_embed | `784 × 4 × 1024` | 0.003 G | ~0 |
| V⑤+V⑦+V⑧ 线性（24 层） | `784 × 24 × 12,582,912` | 236.760 G | 86.68% |
| V⑥ 注意力 QKᵀ+PV（24 层） | `24 × 2 × 784² × 1024` | 30.212 G | 11.06% |
| V⑨ merger | `196 × 25,165,824` | 4.933 G | 1.81% |
| **合计** | | **273.14 GMAC** | 100% |

**单 block 单 patch 线性 MAC 分解**

| 子项 | 计算式 | MAC | 占 block 线性 |
|---|---|---:|---:|
| `attn.qkv` | `1024×3072` | 3,145,728 | 25.0% |
| `attn.proj` | `1024×1024` | 1,048,576 | 8.3% |
| `mlp.linear_fc1` | `1024×4096` | 4,194,304 | 33.3% |
| `mlp.linear_fc2` | `4096×1024` | 4,194,304 | 33.3% |
| **小计** | | **12,582,912** | 100% |

这里要注意参数量和 MAC 的区别。`12,596,224 = 12,582,912 + 9,216(bias) + 4,096(LN γ/β)` 是单 block 的参数量（含 bias 与 LN），比 GEMM MAC 多 13,312。把 12,596,224 当 MAC 用只会高估 0.1%，但概念上两者不是一回事。

**注意力量级**：`2 × 784² × 1024 = 1.259 GMAC/block`，是单 block 线性 MAC（`784 × 12,582,912 = 9.865 G`）的 **12.8%**，是整图的 **11.1%**。M=784 时注意力还很便宜；`N²` 项在 1024² 上会翻到 39%。

**折合文本 token**（文本 prompt 每 token ≈ 1.896 GMAC，见 8.1）：
`273.14 / 1.896 ≈ 144` 个 prompt token；`546.3 GFLOP`。
每视觉 token：`273.14 / 196 ≈ 1.393 GMAC` —— 和 **1 个文本 prefill token（1.896−0.509 lm_head ≈ 1.387 GMAC）几乎相等**。巧合但好用：**1 个视觉 token ≈ 1 个文本 prefill token 的算力**。

### 通用公式（任意 H_px、W_px、T）

$$N=\frac{T}{2}\cdot\frac{H_{px}}{16}\cdot\frac{W_{px}}{16},\qquad M=\frac{N}{4}=\frac{T}{2}\cdot\frac{H_{px}}{32}\cdot\frac{W_{px}}{32}$$

$$\boxed{\text{MAC}_{vis}\approx N\cdot\underbrace{(1{,}572{,}864+24\times12{,}582{,}912+6{,}291{,}456)}_{309{,}854{,}208}+49{,}152\cdot N\cdot N_{frame}}$$

其中 `N_frame = (H/16)(W/16)`（因为 `cu_seqlens` 按帧切段，注意力只在帧内做）；单帧图像 `T=2` 时 `N_frame = N`，退化为 `49,152·N²`。前两项分别是 patch_embed、24 层线性、merger（`M×25,165,824 = N×6,291,456`）；LN/RoPE/GELU/softmax 合计 < 0.8%，未计入。

### 另一算例：1024×1024、T=2（N=4096，M=1024）

| 阶段 | 计算式 | MAC | 占比 |
|---|---|---:|---:|
| patch_embed | `4096 × 1,572,864` | 6.442 G | 0.31% |
| 24 层线性 | `4096 × 24 × 12,582,912` | 1,236.951 G | 59.08% |
| 24 层注意力 | `24 × 2 × 4096² × 1024` | 824.634 G | 39.39% |
| merger | `1024 × 25,165,824` | 25.770 G | 1.23% |
| **合计** | | **2,093.80 GMAC** | 100% |

`= 4.19 TFLOP ≈ 1,104 个 prompt token`。相对 448²：token 数 5.2×，算力 **7.7×**（注意力的 `N²` 把成本推上去）；merger 输出 1024 个 token，是 448² 的 5.2×。

### 视频算例（T=8、448²，t=4，N=3136，M=784）

线性 `3136 × 309,854,208 = 971.7 G` + 注意力 `24 × 4段 × 1.259 G = 120.9 G` + merger `784 × 25.17 M = 19.7 G` + patch `4.93 G` ≈ **1,117 GMAC ≈ 589 prompt token**。
若误按整段 `[0,3136]` 做 dense attention：`24×2×3136²×1024 = 483.4 G`（**4×**），且跨帧串味。这就是 `cu_seqlens` 的实际价值。

### 与 decode 的对照

| 指标 | 文本 decode（每 token） | 视觉塔（每图 448²） |
|---|---:|---:|
| MAC | 1.896 G | 273.1 G |
| 读权重 | 1.94 GB | 0.342 GB（全塔，只读一遍） |
| 算术强度 | ~1 MAC/B | **~799 MAC/B** |
| 主导算子 | GEMV（M=1） | GEMM（M=784） |
| 引擎利用率 | 受带宽限 | 受算力限（可打满） |

---

## 7.4 视觉塔的矩阵引擎映射

**结论：视觉塔 88.9% 的 MAC 是纯 GEMM，可整体上引擎；剩下 11.1% 的注意力被 softmax 切成三段，中间必须回向量单元。**

| 算子 | 形式 | (M, K, N) | 上引擎 | 落点/说明 |
|---|---|---|---|---|
| V① patch_embed | GEMM | (784, 1536, 1024) | 可 | Conv3d 直接降为 GEMM（stride=kernel，无需 im2col）；像素输入量化需验证 |
| V② pos_embed | gather+加权和 | — | 不可 | VPU；4 tap/行，表 2.36 MB 常驻 L2 |
| V③ 2D RoPE | elementwise | — | 不可 | VPU；FP32 角度表，q/k 需 FP32 往返 |
| V④ LayerNorm ×2 | 行归约 | — | 不可 | VPU；FP32 统计量，输出后置 MXFP8 量化 |
| V⑤ qkv | GEMM | (784, 1024, 3072) | 可 | bias epilogue 需支持 3072 维 |
| V⑥ QKᵀ | batched GEMM | 16×(784, 64, 784) | 可 | `1/8` 折进 Q 的 scale；FP32 输出给 softmax |
| V⑥ softmax | 行归约 | — | 不可 | VPU；FP32 + 行 max；与上/下段需 tiling 融合 |
| V⑥ PV | batched GEMM | 16×(784, 784, 64) | 待验 | P 需 E4M3（per-32 scale），精度待验；不行就整段回 VPU |
| V⑦ proj | GEMM | (784, 1024, 1024) | 可 | 输出进残差，保持 FP16 |
| V⑧ fc1 | GEMM | (784, 1024, 4096) | 可 | 最大单项；建议与 GELU+量化融合 |
| V⑧ GELU(tanh) | elementwise | — | 不可 | VPU/SFU；FP32 内做，再量化 |
| V⑧ fc2 | GEMM | (784, 4096, 1024) | 可 | — |
| V⑨ LN | 行归约 | — | 不可 | VPU；1024 维（不是 4096） |
| V⑨ fc1 | GEMM | (196, 4096, 4096) | 可 | M 偏小（196），利用率约 25–50% |
| V⑨ GELU(erf) | elementwise | — | 不可 | VPU；erf 需多项式，勿用 tanh 顶替 |
| V⑨ fc2 | GEMM | (196, 4096, 2048) | 可 | 同上；建议 FP16 权重 |
| V⑩ masked_scatter | 数据搬运 | — | 不可 | DMA；803 KB |

**引擎侧量化策略（按优先级）**

1. **权重**：全部 MXFP8（331.4 MB + 3.1% scale）。例外：`merger.linear_fc1/fc2` 共 25.2 M（7.6%）建议 FP16——它们是唯一直接决定注入数值的层。
2. **激活**：所有 GEMM 的 A 操作数都需 MXFP8，故每个 GEMM 之间插一次 `amax(32) → E8M0 → E4M3` 量化。每 block 约 6 个量化点（LN1 后、Q/K、P、attn_out、LN2 后、GELU 后），整图约 106 M 元素过量化流水。
3. **残差流**：FP16（或 FP32），**禁止**量化；24 层累加，MXFP8 会累积到不可用。
4. **softmax / LN 统计量**：FP32，行最大值与行和。
5. **scale 折叠**：`attention_scaling=1/8`、`1/√64` 都可折进 Q 的 per-block E8M0，避免单独一遍缩放。
6. **`P` 的量化是唯一的真实风险点**：若 E4M3 下 attention 输出误差 > 1%，把 V⑥ 整段（30.2 G，11.1%）留在 VPU 用 FP16，其余 88.9% 仍上引擎。

**引擎算力估算**：273.14 GMAC/图。若引擎 100 TMAC/s（MXFP8），单图 ≈ **2.7 ms**，其中 GEMM 部分 2.4 ms；权重 342 MB 只需 0.34 ms（1 TB/s），**算力受限，不是带宽受限**——这正是引擎最擅长的形态。

---

## 7.5 视觉塔的坑清单（速查）

| # | 坑 | 正确做法 | 错了会怎样 |
|---|---|---|---|
| 1 | 注意力因果性 | `is_causal=False`，全 784 列可见 | 每个 patch 只看前一半，特征整体退化，不报错 |
| 2 | packed 变长 | `cu_seqlens` **按帧**切段（`merge_temporal=False`），`max_seqlen=h·w` | 多图/跨帧串味；视频 MAC 4× |
| 3 | 归一化 | **LayerNorm，γ+β，减均值**（文本 RMSNorm 是 `1+w`、无 β） | 少 β、多 `1+`，全塔数值偏移 |
| 4 | 激活 | block 用 **GELU-tanh**，merger 用 **GELU-erf**；文本是 SiLU | 用 SiLU 顶替 → 系统性偏差；用 tanh 顶 erf → 1e-3 级误差 |
| 5 | MLP 结构 | **2 矩阵、无门控**（文本是 SwiGLU 3 矩阵） | 多算 50% MAC 与权重 |
| 6 | merger 顺序 | `use_postshuffle_norm=False` → **先 LN(1024) 再 view(4096)** | 统计维度从 1024 变 4096，权重 shape 也不匹配 |
| 7 | merger 的 M | **M=N/4=196**，不是 784 | MAC 4× 高估（19.7 G vs 4.93 G） |
| 8 | token 顺序 | 全程 **block-major（2×2 块序）**，不是光栅序 | pos id / RoPE / merge 分组全错位 |
| 9 | 位置编码 | 学习表 **双线性 4-tap**、`align_corners=True`、`S=48` | 换成 `align_corners=False` 偏移半格，静默错误 |
| 10 | RoPE 形式 | 2D 轴向，16 频，**half-split** `(d,d+32)`，**全 64 维** | 用 interleaved 配对或只转一半维 → 位置信息错 |
| 11 | bias | **每个 Linear 都有 bias**（文本全无） | 漏加 → 每层偏移，累积后可测 |
| 12 | GQA | **无**，16 Q 头 = 16 KV 头 | 按 GQA 复用 KV 会算错 |
| 13 | patch_embed | `stride == kernel`，实为 GEMM；`T/2` 不是 `T` | 当滑窗卷积设计 → 大量无用数据复用逻辑 |
| 14 | 输出取哪个 | 注入用的是 `pooler_output`（196×2048），不是 `last_hidden_state`（784×1024） | 维度对不上或注入未 merge 特征 |
| 15 | deepstack | `deepstack_visual_indexes=[]` → 只取最后一层，无多层注入 | 按 Qwen3-VL 的 3 处 deepstack 预留带宽/接口 → 白做 |
| 16 | 精度 | 残差 FP16+，LN/softmax FP32，merger 权重建议 FP16 | 全链 MXFP8 → 24 层误差累积，cos 掉到 0.99 以下 |

---

## 7.6 输出头与采样

`final RMSNorm → lm_head → logits[248320] → 采样` 这一段占 decode 总算力的 26.8%、总带宽的 27%，却只产出 1 个整数。它是全模型最扁的 GEMV（N=248320, K=2048），也是唯一必须回到 CPU 的环节。

```
x [2048] ──► model.norm ──► lm_head (W_embᵀ) ──► logits [248320] FP32 ──► 采样 ──► token id
                                                          │
                                                    (CPU, 1 MB)
```

### O1 final RMSNorm
**作用**：最后一层归一，把隐状态尺度拉到 logits 的合适量级。
**调用**：`Qwen3_5TextModel.forward` 末尾 → `self.norm(hidden_states)`（`Qwen3_5RMSNorm`）
**公式**：`y = x · rsqrt(mean(x²)+1e-6) · (1+w)`，`w [2048]`
**形状**：`[2048] → [2048]`
**MAC 推导**：6.1 K MAC（0.0003% of 508.56 M）
**精度**：FP32
**坑**：这是**唯一一次**对“整条残差流”做的最终归一，它的增益 `1+w` 直接决定 logits 的温度尺度。增益量化误差 → 等价于**全局温度被改**（0.5% 的增益误差 ≈ 温度 0.5% 的偏移），采样分布整体偏移。**这一层的增益必须 FP32 存储**（2 KB，别省）。

### O2 lm_head（= embed_tokensᵀ）
**作用**：把 2048 维隐状态投到 248320 维词表，得到每个 token 的分数。
**调用**：`Qwen3_5ForCausalLM.forward` → `slice_logits_to_keep(hidden_states, logits_to_keep)` → `self.lm_head(hidden_states)`（`modeling_qwen3_5.py:1721`）；权重由 `_tied_weights_keys = {"lm_head.weight": "model.embed_tokens.weight"}` 绑定
**公式**：`logits[v] = Σ_{k=0}^{2047} E[v,k] · x[k]`，v = 0..248319（`E` 就是 embedding 矩阵，`lm_head` 是它的转置）
**形状**：权重 `[248320, 2048]`（**checkpoint 里没有这个张量**，只有 `model.language_model.embed_tokens.weight`）；输入 `[2048]`；输出 `[248320]`
**MAC 推导**：

```
248320 × 2048 = 508,559,360 = 508.56 M MAC / token
折 FLOP：1.017 GFLOP
权重字节：508.56 M × 1.031 B = 524.3 MB（MXFP8）
```
**精度**：权重 MXFP8（per-32 block，沿 K=2048 分块），**FP32 累加**，输出 **FP32 logits**。
**坑**：
1. **`slice_logits_to_keep` 是关键**。默认 `logits_to_keep=0` → 只保留最后一个位置。所以 prefill 一个 4096 token 的 prompt，`lm_head` 也只算 1 行（508.56 M），而不是 4096 行（2.08 TMAC）。自研实现若把 prefill 的所有位置都投到词表，会在 prompt 阶段凭空多花 4096 倍算力——这是“训练脚本直接搬来推理”最典型的灾难。
2. **算术强度 0.97 MAC/byte**，与 MLP 同级，是纯带宽题。310 GB/s 下 524.3 MB 要 **1.69 ms**；要跟上，引擎只需 **301 GMAC/s**——但**输出侧**是 248320 个 FP32，写出 1 MB。
3. **N=248320 = 512 × 485**，天然按 512 对齐，分 485 个 tile，无尾块。这是词表被 pad 到 512 倍数的好处，tiling 时直接用。
4. **权重共享省的是容量不是带宽**：表只存一份（省 524 MB 容量），但每 token 要读两次——查表读 1 行（2.1 KB）+ `lm_head` 读整个矩阵（524 MB）。**容量省了，带宽没省。**
5. FP8 量化对 **greedy** 最危险：两个候选 logit 差 0.05 时，2%~4% 的相对误差（在 |logit|≈10 时绝对误差 0.2~0.4）会直接翻转 argmax。建议：FP8 先出 top-32 候选，再用 FP16 对这 32 个重算精确 logit（32×2048 = 65 K MAC，成本可忽略）。这是“混合精度 top-k 修正”，对温度采样也同样有益。

### O3 logits 输出缓冲
**作用**：存放 248320 个分数，交给采样。
**调用**：引擎输出 → DMA → DRAM → CPU（或共享内存）
**公式/形状**：`[248320]`，FP32 → **993,280 B = 0.947 MiB ≈ 1 MB**；FP16 → 0.474 MB
**MAC 推导**：0（纯搬运）
**精度**：**FP32**。理由：softmax 前要减 max，logits 幅值可达 ±30，FP16 在 30 附近的 ulp 是 0.0156，相对 5e-4——对温度 1.0 的采样影响 <0.1%，但**对 argmax 边界（差 <0.01）是致命的**；BF16 更差（8-bit 尾数，ulp 0.25 @30）。
**坑**：
1. 访存代价：写 1 MB + CPU 读 1 MB = **2 MB/token**，占 1.941 GB 的 **0.1%**——带宽可忽略，但**延迟**不可忽略：1 MB DMA @310 GB/s = 3.2 µs；若走 PCIe 4.0 x4（~8 GB/s）则 **125 µs**，在 6.26 ms 的 token 预算里占 2%。离散卡方案要按 PCIe 带宽重算。
2. **不要为了省 0.5 MB 把 logits 降成 FP16**。省下的是 0.03% 的带宽，赔上的是 greedy 的稳定性。
3. 若引擎按 N-tile 分块输出（485 个 tile），**softmax 的 max/denominator 需要跨 tile 归约** → 要么全量落 buffer 再扫两遍，要么在引擎侧做 online-softmax（running max + rescale）。前者多读 1 MB，后者多一个跨 tile 的串行依赖。推荐前者（1 MB 太小，不值得为它设计 online 算法）。

### O4 采样：logits → token id
**作用**：把分数变成概率，按策略抽样。
**调用**：CPU 侧 `GenerationMixin` / 自研 sampler（`transformers` 里是 `LogitsProcessor` 链 + `torch.multinomial`）
**公式**（完整链路）：

```
1) 温度：        z_i = logit_i / T
2) 数值稳定：    m = max_i z_i,   p_i = exp(z_i − m) / Σ_j exp(z_j − m)
3) top-k：       保留 p 最大的 k 个，其余置 0，再归一化
4) top-p：       π = argsort(p, 降序),  c_k = Σ_{j=1..k} p_{π(j)}
                 k* = min{ k : c_k ≥ p_top }
                 S  = { π(1), …, π(k*) }
                 p'_i = p_i · 1[i ∈ S] / Σ_{j∈S} p_j
5) 抽样：        u ~ U(0,1)，取最小的 t 使 Σ_{j≤t} p'_{π(j)} ≥ u
```
**形状**：`[248320] → [248320] → 标量`
**MAC 推导**：
```
exp        248,320 次
求和       248,319 次（建议成对/树形，或分块 4 路）
排序       O(N log N) ≈ 248,320 × 18 ≈ 4.5 M 次比较
```
**精度**：**FP32**（`softmax(..., dtype=torch.float32)`，与 attention 里的 softmax 一致）。FP32 累加 248K 项的误差 ≈ √248320 × 6e-8 ≈ 3e-5 相对，够用；FP16 累加会到 1e-2，不可接受。
**坑**：
1. **必须先减 max**。`T=0.1` 时 logits/0.1 放大 10 倍，`exp(300)` 直接溢出 FP32（上限 ~88）。这是所有采样实现的第一条铁律，也是 BF16/FP16 logits 不能直接 `exp` 的原因（FP16 上限 65504，`exp(11)` 就爆）。
2. **温度对分布的作用**：`T→0` 时 `p_i/p_j = exp((z_i−z_j)/T) → 0`（只要 `z_i < z_j`）→ 退化为 **one-hot argmax**；`T→∞` 时 `p → uniform`；熵 `H(T)` 对 T 单调递增。所以 `T=0` 不要真的除零，走 **argmax 分支**（可以在引擎侧用一次 max 归约完成，省掉整个 CPU 往返）。
3. **top-p 与 top-k 的顺序有影响**：先 top-k 再 top-p ≠ 先 top-p 再 top-k。HuggingFace 的默认是 `top_k=0`（关）+ `top_p=1.0`（关）+ `T=1.0`，即纯多项式采样；本模型的 `generation_config.json` 实际默认值需核对。
4. **排序是 CPU 上的真瓶颈**。248K 元素的 `std::sort` ≈ 4.5 M 次比较，在 1 GHz 标量核上 **5~10 ms**（量级估算），比整个 token 的 6.26 ms 还长——直接吃掉投机解码的全部收益。**必须用部分选择**：`nth_element` / 大小为 k 的堆（O(N log k) ≈ 1.4 M 次比较，1~2 ms），或按 logit 做 256 桶直方图 + 二分找阈值（O(N)，<0.5 ms）。
5. **top-p 的 k\* 是数据相关的动态值**：SIMD 宽度用不满、分支不可预测。这是它必须离开 NPU 的结构性原因。

### O5 为什么采样必须放 CPU

| 原因 | 具体表现 |
|---|---|
| **控制流依数据** | `k*`（top-p 截断点）和排序长度都是运行时才知道的；NPU 的 SIMD/脉动阵列对“变长 + 分支”零效率，还会引入 warp divergence |
| **随机数** | 需要可复现的 per-request RNG（种子、状态），CPU 侧有成熟实现；NPU 上的 RNG 要么没有、要么不可复现（对测试/回归是灾难） |
| **动态形状** | 采样是 `[N]→标量`，N=248320 固定但内部索引不规则；不适合任何规整 tiling |
| **强串行依赖** | 采样出的是**标量 token id**，下一步要立刻用它做 embedding 查表（D2/O2），再启动下一个 GEMV。这个“标量回灌”是天然的 host 边界，放在 CPU 反而省一次 device↔host 同步 |
| **算力为零** | 248K 次 exp ≈ 0.5 M 运算，占 1.9 GMAC 的 0.03%。放 CPU 不心疼 |
| **精度** | softmax 的 max + 求和要 FP32（甚至 FP64）累加；NPU 的 FP16 累加路径不够 |

**推荐划分（给架构的折中方案）**：
- **max / sum / 直方图归约放 NPU**（读 1 MB，一次 reduction pass，几 µs），CPU 只拿回 `(max, denom, 256-bin histogram)`；
- **top-p 阈值在 CPU 上用直方图二分求**（O(256)，微秒级），再回 NPU 做一次“按阈值筛 + 前缀和”；
- **argmax（T=0）走纯片上路径**，只有 token id 过 host，省掉 1 MB 往返；
- **最终抽样（1 次 `u` 比较）在 CPU**，因为它是纯标量 + RNG。

### O6 输出头总账

| 项 | 数值 | 占比（vs 1.941 GB / 1.896 GMAC） |
|---|---:|---:|
| final RMSNorm | 6.1 K MAC | 0.0003% |
| **lm_head** | **508.56 M MAC / 524.3 MB** | **26.8% 算力 / 27.0% 带宽** |
| logits 缓冲 | 1.0 MB FP32（写+读 2 MB） | 0.1% 带宽 |
| 采样（CPU） | ~0.5 M 运算 + 排序 | 0.03% 算力，**延迟 1~10 ms（排序实现决定）** |
| **小计** | **508.56 M MAC / 526 MB** | |

**三个数字记住**：
1. **508.56 M MAC 是“每生成一个 token”的成本，不是“每个 prompt token”**（`slice_logits_to_keep`）。这是投机解码和长 prompt 场景下最容易被算错的一笔账。
2. **1.69 ms / token 只花在 lm_head 的权重读取上**（310 GB/s），是 6.26 ms 总预算的 27%——它和 MLP（3.01 ms）一起构成 75% 的带宽。
3. **采样放 CPU 的代价不是算力，是延迟**：一个没优化的 248K 全排序（5~10 ms）能让 160 tok/s 变成 60 tok/s。**排序算法是端侧推理栈里性价比最高的一个优化点**，比换矩阵引擎容易得多。

---
---

# 第四部分　核算与映射

---

# 第 8 章　三本账：算力 / 带宽 / 缓存

每 token 约 1.90 GMAC，其中 MLP 占 48%、lm_head 27%、DeltaNet 19%、Attention 3%。每 token 要读 1.94 GB 权重（MXFP8），这是 decode 速度的决定因素。缓存则是"一小一大"：DeltaNet 状态恒定 18.9 MB，KV cache 随上下文线性增长。

## 8.1 算力账（decode，S 较短）

| 组件 | 数量 | 单个 MAC | 小计 | 占比 |
|---|---:|---:|---:|---:|
| 层型 A（DeltaNet） | 18 | 59.62 M | 1,073.2 M | 56.6% |
| 层型 B（Attention，不含 KV 项） | 6 | 52.43 M | 314.6 M | 16.6% |
| Attention 的 QKᵀ + AV | 6 | 4096·S | 24,576·S | 随 S 涨 |
| lm_head | 1 | 508.56 M | 508.6 M | 26.8% |
| embedding | 1 | 0 | 0 | 0% |
| **合计（S 较短）** | | | **1,896 M** | |
| **S = 4096 时** | | | **1,997 M** | |
| **S = 32768 时** | | | **2,701 M** | |

1.90 GMAC 折成浮点口径是 3.79 GFLOP / token。

从这张表能看出三件事。MLP 是最大项（906M，占 47.8%），结构也最规整，优化它最省力。lm_head 是第二大的单个算子（508M，26.8%），形状最扁。DeltaNet 占了 75% 的层数，算力却不到一半，因为每层比 attention 层小。

## 8.2 带宽账（decode，MXFP8 = 8.25 bit = 1.031 B/参数）

| 项 | 参数量 | 字节 |
|---|---:|---:|
| 层型 A × 18 | 1,058.7 M | 1.092 GB |
| 层型 B × 6 | 314.6 M | 0.324 GB |
| lm_head（= embed） | 508.6 M | 0.524 GB |
| **合计** | **1,881.8 M** | **1.941 GB** |

| 附加流量 | 大小（S=4096） | 说明 |
|---|---:|---|
| KV cache 读 | 50 MB | 6 层 × 2 头 × 256 × 4096 × 2 × 2B |
| DeltaNet 状态读写 | 37.7 MB | 18.9 MB × 2（必须常驻 SRAM，不能落 DRAM） |
| 激活 | < 2 MB | 可忽略 |

**速度上限**：

```
带宽 310 GB/s ÷ 1.941 GB/token = 160 token/s（理论上限）
实际效率 60~70%            → 96~112 token/s
```

这个结果和 7B 模型完全不同。7B 在 310 GB/s 下被死死压在 30 tok/s，而 2B 能跑满一百多 token/s。端侧做 2B 的意义就在这里。

## 8.3 缓存账

| 项 | 公式 | S=4K | S=32K | S=256K |
|---|---|---:|---:|---:|
| DeltaNet 状态（FP32） | 18.9 MB 恒定 | 18.9 MB | 18.9 MB | 18.9 MB |
| DeltaNet conv 状态 | 0.66 MB 恒定 | 0.66 MB | 0.66 MB | 0.66 MB |
| Attention KV（FP16） | 12,288·S B | 50 MB | 402 MB | **3.22 GB** |
| **合计** | | **70 MB** | **422 MB** | **3.24 GB** |

这张表带出两个必须做的设计决策。

一是 18.9 MB 的状态必须常驻片上 SRAM。落到 DRAM 的话，每 token 会多 37.7 MB 流量（+1.9% 带宽），而且 24 层之间存在强时序依赖，DRAM 延迟会直接暴露出来。

二是 256K 上下文需要 3.2 GB 的 KV 空间。标称 4 GB LPDDR 的边缘设备跑不了满上下文，实际产品要么限制上下文，要么上 FP8 KV cache（降到 1.6 GB）。

---
---

# 第 9 章　时间线：一个 token 走完全程

把前面算出的 MAC 和字节数按执行顺序排一遍，看时间到底花在哪。三种负载分开算：decode 一个 token、prefill 一段 prompt、一张图的前向。

## 9.1 Decode 一个 token（S=4096）

```
 1  查表 embed[token_id]                    2 KB DRAM 读          VPU
 2  for layer 0..23:
 3      RMSNorm                             ~2 K MAC              VPU
 4      ┌ 若为 DeltaNet（18 层）：
 5      │   in_proj_qkv + z + a + b         16.84 M MAC          引擎     ← 读 17.4 MB
 6      │   conv1d + SiLU                   24.6 K MAC + 6144 silu VPU    ← 读写 36 KB 状态
 7      │   L2 norm(q,k)                    8 K MAC               VPU
 8      │   β、g 计算                        16 维超函数           VPU     ← FP32 关键路径
 9      │   ── delta 递推 ──                0.79 M MAC            VPU      ← 串行，读+写 2.1 MB
10      │   RMSNormGated + silu             ~2 K MAC              VPU
11      │   out_proj                        4.19 M MAC           引擎     ← 读 4.3 MB
12      ├ 若为 Attention（6 层）：
13      │   q/k/v proj                      10.49 M MAC          引擎     ← 读 10.8 MB
14      │   q/k norm + RoPE                 ~1 K MAC              VPU
15      │   KV cache 追加                   0 MAC                 DMA      ← 写 2 KB
16      │   Q·Kᵀ + softmax + p·V            16.8 M MAC           引擎/VPU ← 读 KV 8.4 MB(FP16)
17      │   ⊙ sigmoid(gate)                 2048 乘               VPU
18      │   o_proj                          4.19 M MAC           引擎     ← 读 4.3 MB
19      └
20      残差加                             2048 加               VPU
21      RMSNorm                            ~2 K MAC              VPU
22      gate/up proj                       25.17 M MAC          引擎     ← 读 25.9 MB
23      silu(g) ⊙ u                        6144 次 exp           VPU
24      ── DMA 在线量化 ──                 0 MAC                 DMA      ← 读写 18 KB
25      down proj                          12.58 M MAC          引擎     ← 读 13.0 MB
26      残差加                             2048 加               VPU
27  final RMSNorm                          ~2 K MAC              VPU
28  lm_head                               508.56 M MAC          引擎     ← 读 524 MB
29  采样（温度/top-p/抽样）                 ~250 K 运算           CPU
```

每 token 的 DRAM 流量约 1.94 GB 权重 + 50 MB KV + 2 KB 激活，合计 **1.99 GB**。除以 310 GB/s 得 **6.4 ms**，也就是 156 tok/s 的上限。

## 9.2 Prefill 一段 prompt（S=1024）

同样的算子序列，但 `M = 1024`，全部 GEMV 变成 GEMM：

| 项 | 数值 |
|---|---|
| 每 token MAC | **1.387 G**（= decode 1.896 − lm_head 0.509；`slice_logits_to_keep` 让 lm_head 只在最后一位算一次，见 7.6 O2） |
| 1024 token 总算力 | 1.42 TMAC = 2.84 TFLOP |
| 若系统 25 TOPS（FP8 稠密） | ≈ 114 ms |
| 权重带宽 | **1.94 GB 只读一次**（被 1024 个 token 复用）|
| 带宽时间 | 1.94 GB ÷ 310 GB/s = 6.3 ms ≪ 114 ms |
| **结论** | prefill 是**算力瓶颈**，decode 是**带宽瓶颈** |

prefill 是算力瓶颈，decode 是带宽瓶颈。这也是阵列要按 prefill 定尺寸的原因：decode 只用到 2~3% 的算力，prefill 能吃满。

## 9.3 一张图的前向（448×448）

| 步骤 | 计算量 | 特征 |
|---|---:|---|
| patch_embed（784 patch） | 1.23 GMAC | 实为 GEMM（stride = kernel），占 0.45% |
| 24 blocks × 784 patch（qkv/proj/fc1/fc2） | 236.76 GMAC | 大 M 的 GEMM，引擎高效 |
| 24 blocks 的 QKᵀ + A·V | 30.21 GMAC | 占 11.1%；`N²` 项，tiling + online softmax |
| merger（**M=196**，不是 784） | 4.93 GMAC | 常见 4× 高估点，见 7.3 |
| **视觉塔小计** | **273.14 GMAC** | ≈ 144 个文本 prompt token |
| 196 个视觉 token 走文本 prefill（不含 lm_head） | 196 × 1.387 ≈ 271.9 GMAC | 等同 196 个 prompt token |
| **一张 448² 图合计** | **≈ 545.0 GMAC ≈ 1.09 TFLOP** | |

**与整段 prompt 的相对量级**：若再加 1024 个文本 token（`1024 × 1.387 = 1.42 TMAC`），整段 prefill 为 **1.97 TMAC**；其中视觉塔（编码 273.1 GMAC）占 **13.9%**，连同 196 个视觉 token 过文本主干（271.9 GMAC）则图像相关共占 **27.7%**。

---

---

# 第 10 章　算子 → 硬件映射

Decode 全程是 M=1 的 GEMV，阵列利用率天然极低，提升空间不在阵列大小，在流水和门控。模型里有两处真串行：delta 递推和 softmax 归约，它们没法被带宽掩盖。另外 lm_head 是最大的单个算子，也是最大的风险点。

## 10.1 分类矩阵

| 类别 | 特征 | 本模型算子 | 归属 |
|---|---|---|---|
| **GEMM / GEMV** | 激活 × 权重矩阵 | 所有 `*_proj`、`lm_head`、`QKᵀ`、`AV`、视觉塔全部 | 矩阵引擎 |
| **归约** | 沿一维求和/最值 | RMSNorm、L2 norm、softmax 分母、amax | VPU |
| **逐元素** | 同形状逐点 | silu、sigmoid、exp、门控乘、残差、RoPE | VPU |
| **串行递推** | 后步依赖前步 | delta 状态更新 | VPU / 专用状态单元 |
| **查表** | 索引取行 | embed_tokens | VPU / DMA |
| **搬运** | 无计算 | KV cache、量化 pass、swizzle | DMA |
| **控制流** | 分支/随机数 | 采样、停止判断 | CPU |

## 10.2 引擎侧的三条结论

**① Decode 全是 GEMV。** 8 个投影加 lm_head 全部是 `M=1` 的矩阵向量乘。阵列在 M=1 时的利用率是 `1/阵列行数`，与阵列多大无关。所以真正的优化点有三个：权重流水保持连续，别让每个 tile 重新握手；细粒度门控配合 DVFS，decode 时 96% 的阵列本来就空闲；以及不要指望把阵列做大。

**② 两处真串行，是延迟的地板。**

- Delta 递推：每层 16 个 head ×（1 次衰减 + 3 次 128×128 matvec），4 个 pass 严格串行；16 head 并行时约 **384 cycle/token/层**（见 6.1 A⑨），串行 18 层。
- Softmax：沿 S 的归约，串行 6 层。

这两处没法靠 DRAM 带宽掩盖，因为它们本身不读大权重，延迟会直接叠加到 tok/s 上。实现上放 VPU 做流水，不要放矩阵引擎——引擎的累加器结构不适合跨 token 保持状态。

**③ lm_head 是最大单算子（27% MAC + 27% BW）。**

一个 [248320, 2048] 的 GEMV，有三点特殊。输出 248,320 维 logits，需要 248,320 × 4B ≈ 1 MB 的输出缓冲；精度最敏感，直接决定采样排序；量化收益也最大，能省 0.52 GB/token。

## 10.3 MTP 与投机解码：能绕开内存墙，但要先算草稿成本

Decode 是带宽瓶颈，根因是每个权重每 token 只用一次。任何提高复用次数的办法都是乘性提速：

| 手段 | 权重复用倍数 | 代价 |
|---|---|---|
| Batching（多用户） | ×B | 端侧通常 B=1 |
| MTP / 投机解码 | 理想 `1/(1−α)`；实际 α=0.7、γ=2 时 1.37×（共享 248K 词表）或 2.25×（γ=3、8K 剪枝草稿头） | 已有 60.8 M 的头 + 18.9 MB 状态快照 + 草稿验证/回滚逻辑 |
| 增大 KV 长度 | 无 | 不改变权重复用 |

3.3× 这个数字拿不到，因为 `1/(1−α)` 是 γ→∞、草稿免费时的渐近上界。实际每产出一个草稿 token 都要跑一遍 508.56 M 的 `lm_head`（524 MB），它本身占单 token 带宽的 27%。把草稿成本计入（推导见 6.4 D6）：

```
γ=2，共享 248K 词表： B_step = 1.941 + 2×0.587 = 3.115 GB，E[emitted] = 2.19 → 1.37×
γ=3，剪枝 8K 草稿头：  B_step = 1.941 + 3×0.080 = 2.181 GB，E[emitted] = 2.53 → 2.25×
```

还有一个容易忽略的架构约束：若规划了投机解码，验证 pass 一次要吃 γ+1 个 token，算力 ×(γ+1)。γ=3 时 `7.58 GMAC / 2.18 GB = 3.5 MAC/B`，引擎需要 **≥1.08 TMAC/s** 才不成为新瓶颈，比 M=1 的 302 GMAC/s 下限高 3.5 倍。要不要规划投机解码，直接决定引擎规格。其余配套（拒绝采样、状态回滚）见 6.4 D6–D7。

---

---

# 第 11 章　量化归属

前置约束：矩阵引擎的输入只接受 FP8 / MXFP8，不接受 BF16；输出可以是 FP32 / FP16 / MXFP8。下面按这个约束逐项归属。

## 11.1 逐项归属

| 组件 | 权重 | 激活/输入 | 输出 | 说明 |
|---|---|---|---|---|
| embed_tokens | BF16/FP8 常驻 | — | — | 0 MAC，只查表；但需与 lm_head 共享，见下 |
| 层型 A `in_proj_qkv` | MXFP8 | MXFP8 | FP32 | 融合投影，切分后 q/k/v 各自用途不同 |
| 层型 A `in_proj_z` | MXFP8 | MXFP8 | **FP16** | 输出直接喂 silu |
| 层型 A `in_proj_a/b` | MXFP8 | MXFP8 | **FP32** | 只有 16 维，量化收益为零，勿单独优化 |
| 层型 A `conv1d` | **FP16** | FP16 | FP16 | 深度卷积，跨 token，MX 块结构不适用 |
| 层型 A `A_log` / `dt_bias` | **FP32** | — | — | 是指数参数，量化的相对误差会被 exp 放大 |
| 层型 A `RMSNormGated` | FP16 | FP32 归约 | FP16 | |
| 层型 A **delta 递推** | — | — | — | **状态 FP32，永不量化**（config 已写死 `mamba_ssm_dtype`） |
| 层型 A `out_proj` | MXFP8 | MXFP8 | **FP32** | 直通残差 |
| 层型 B `q/k/v_proj` | MXFP8 | MXFP8 | FP32→FP16 | |
| 层型 B `q_norm/k_norm` | FP16 | **FP32** | — | |
| 层型 B **RoPE** | — | **FP32** | — | 角度误差 → 相位漂移 |
| 层型 B **softmax** | — | **FP32** | — | 分母动态范围大 |
| 层型 B `QKᵀ` | — | FP16 | — | 分数误差会被 softmax 放大；不建议 MX |
| 层型 B `p·V` | — | **FP16**（p 不建议 FP8） | — | p ∈[0,1] 且长尾，E4M3 相对误差 ~3%，加权平均**不改善**相对精度，比 FP16 差 64× |
| 层型 B `o_proj` | MXFP8 | MXFP8 | **FP32** | 直通残差 |
| 层型 B 门控 `sigmoid(gate)` | — | FP16 | — | |
| MLP `gate/up_proj` | MXFP8 | MXFP8 | **FP16** | 下一步 silu |
| MLP **SwiGLU** | — | **FP16** | — | silu 负半轴放大误差 |
| MLP `down_proj` | MXFP8 | MXFP8（**DMA 在线量化**） | **FP32** | 直通残差 |
| **lm_head** | MXFP8（待验证） | MXFP8 | **FP32** | 见 11.3 |
| 视觉塔 GEMM | MXFP8 | MXFP8 | FP32 | 结构规整，无 massive activation；例外见 11.4 |
| MTP 层 | MXFP8 | MXFP8 | FP32 | 与层型 B 相同 |

## 11.2 三条硬规则

残差流永不进 FP8。误差跨 24 层累加，中途没有重新归一的机会，所以写残差的投影输出必须是 FP32。

DeltaNet 的状态、`A_log`、`dt_bias` 永不离开 FP32。config 里的 `mamba_ssm_dtype: float32` 已经把这个决定焊死了；`g = −exp(A_log)·softplus(...)` 里的 exp 会放大任何相对误差。

MXFP8 输出只在链式 GEMM 之间考虑。本模型没有明显的链式 GEMM（每个投影后面都接了非线性或归约），所以 v1 只需要 FP32 和 FP16 两档输出。

## 11.3 lm_head 的特殊处理

| 观察 | 数字 |
|---|---|
| 占 decode MAC | 27% |
| 占 decode 权重带宽 | 27%（0.52 GB/token） |
| 与 embedding 共享 | 是（省 508M 容量，不省带宽） |
| logits 维度 | 248,320 |

建议分三步验证。

1. 先做 MXFP8（W8A8），FP32 累加、FP32 输出，测 top-1 token 一致率，门槛 >95%。
2. 不达标就把 `lm_head` 提到 FP16 权重（+0.52 GB/token，tok/s 从 156 降到 ~118）。
3. 仍不达标则把 `lm_head` 移到 VPU 做 FP16 GEMV。算力上完全可行：508M × 30 tok/s = 15 GMAC/s = **31 GFLOPS**，0.5 TOPS 的 VPU 只用 6% 的 duty。

共享权重还有一个隐性代价：量化策略必须同时满足查表和 lm_head 两个用途。`embed_tokens` 做查表时对精度要求低，做 lm_head 时要求高，两者冲突只能取高。

## 11.4 视觉塔的量化

视觉塔是整个模型里最容易量化的部分：标准 ViT，没有门控或递推，GELU 两侧有界。但有四处必须单独处理。

| 项 | 归属 | 理由 |
|---|---|---|
| 24 个 block 的全部 GEMM | MXFP8 | 结构规整；`1/8` 的 attention scaling 可折进 Q 的 per-block E8M0 |
| `patch_embed` | MXFP8 或 FP16 | 唯一直接吃原始像素的算子，量化前无任何前置平滑；只占 0.45% 算力 |
| 残差流 | FP16 / FP32 | 24 层累加，中途量化到 MXFP8 会累积到不可用 |
| LN 统计量、softmax | FP32 | 行均值 / 方差 / 行最大值 |
| `merger.linear_fc1/fc2`（25.2 M，7.6%） | 建议 FP16 | 唯一直接决定注入文本序列数值的层 |
| `A·V` 里的 P | 风险点 | P ∈ [0,1] 且质量集中在 [0, 0.05]，E4M3 相对误差 ~6%；若 attention 输出相对 L2 误差 >1%，V⑥ 整段（30.2 G，11.1%）回 VPU 用 FP16 |

量化点密度方面，每个 block 约 6 处（LN1 后、Q/K、P、attn_out、LN2 后、GELU 后），整图约 **106 M 元素**要过一遍 `amax(32) → E8M0 → E4M3` 流水。这是 1% 量级的向量开销，排 pipeline 时不要漏掉。

W4A4 不建议采用：视觉 token 直接进文本残差流，误差没有重新归一的机会。

---

---

# 第五部分　结论

---

# 第 12 章　对芯片设计的结论与待验证清单

前面各章的账归拢成九条结论，再和 7B 方案的资源需求对照一次，最后列出还没验证的假设。

## 12.1 九条结论

| # | 结论 | 依据 |
|---|---|---|
| 1 | decode 每 token 读 1.94 GB 权重，310 GB/s 下理论上限 160 tok/s，实际 96–112 tok/s | 8.2 |
| 2 | prefill 是算力瓶颈，decode 是带宽瓶颈，两者需要的资源完全不同 | 9.2 |
| 3 | 阵列按 prefill 定尺寸；decode 只用到 2–3%，靠门控 + DVFS 省电 | 10.2 |
| 4 | 18.9 MB 的 DeltaNet 状态必须常驻 SRAM，且是 FP32（config 写死） | 8.3 |
| 5 | 6 层 attention 的 KV cache 在 256K 上下文下要 3.2 GB，满上下文不可行，除非用 FP8 KV | 8.3 |
| 6 | 两处真串行（delta 递推、softmax）是延迟地板，必须放 VPU 并流水化 | 10.2 |
| 7 | lm_head 是最大单算子（27% MAC + 27% BW），是量化收益与风险的双重焦点 | 11.3 |
| 8 | MTP 头是现成的投机解码抓手，但净收益取决于草稿头词表：γ=2 共享 248K 词表只有 1.37×，剪枝到 8K 才有 2.25× | 10.3 / 6.4 D6 |
| 9 | 引擎吞吐下限 302 GMAC/s（M=1 的 MXFP8 GEMV，每 1 B/param 需 ~1 GMAC/s）；若规划投机解码，按 1 TMAC/s 定规格 | 6.3 M6 / 10.3 |

## 12.2 与 7B 方案的资源对比

| 项 | 7B MXFP8 | 本模型 2B |
|---|---|---|
| 每 token 权重 | ≈7.3 GB | 1.94 GB |
| 310 GB/s 下 tok/s | 30–40 | 96–112 |
| 主存需求 | 12–16 GB | 4–6 GB（短上下文） |
| 系统算力 | 25–32 TOPS | 10–16 TOPS 足够 |
| SRAM | 8–16 MB | ≥ 24 MB（状态 19.5 MB + 工作区） |
| 特殊之处 | 无 | 视觉塔 + MTP + FP32 状态 |

最反直觉的一条是 SRAM：2B 比 7B 更吃 SRAM，而不是更省。18 层 DeltaNet 的 FP32 状态是 18.9 MB，这部分不随模型大小缩放，而 7B 模型没有这个结构。SRAM 预算要以 24 MB 为起点重算。

## 12.3 待验证清单

| # | 待验证项 | 方法 | 影响 |
|---|---|---|---|
| 1 | 权重实际形状（本文按 config 推导） | 解析 safetensors header | 若 `q_proj` 不是双宽，attention 参数差 4.19M |
| 2 | 视觉塔是否必须在端侧跑 | 产品形态决定 | 省 331M 参数 + 24 blocks |
| 3 | MTP 是否可用 | 需配套草稿验证 + 8K 剪枝草稿头 | 决定能否拿到 1.37×（共享词表）或 2.25×（8K 词表） |
| 4 | KV cache 能否降到 FP8 | 长上下文精度测试 | 256K 时省 1.6 GB |
| 5 | lm_head 的 MXFP8 精度 | top-1 一致率 | 决定 27% 带宽能否压缩 |
| 6 | delta 递推的串行延迟 | RTL/仿真 | 决定 tok/s 的真实上限 |
| 7 | `in_proj_a/b` 的 N=16 GEMV 如何处理 | 合并投影 vs VPU | 影响引擎利用率 |
| 8 | 视觉塔的 patch 数上限 | 输入分辨率规格 | 决定 prefill 峰值算力 |

---
---

# 附录 A　符号表

| 符号 | 含义 | 值 |
|---|---|---|
| `H` | hidden_size | 2048 |
| `L` | 层数 | 24 |
| `L_lin` | DeltaNet 层数 | 18 |
| `L_attn` | 全注意力层数 | 6 |
| `V` | vocab_size | 248320 |
| `I` | intermediate_size | 6144 |
| `n_h` | attention 查询头数 | 8 |
| `d_h` | attention head_dim | 256 |
| `n_kv` | KV 头数 | 2 |
| `n_lin_h` | DeltaNet 头数 | 16 |
| `d_k, d_v` | DeltaNet 头维 | 128, 128 |
| `S` | 上下文长度 | 变量，≤262144 |
| MAC | 一次乘加 | 2 FLOP |

---

# 附录 B　复现脚本

```python
H, I, V = 2048, 6144, 248320
n_h, d_h, n_kv = 8, 256, 2          # 全注意力：8 头 × 256 维，2 个 KV 头
n_lh, d_k, d_v = 16, 128, 128       # DeltaNet：16 头 × 128 维
L_lin, L_attn = 18, 6

# 层型 A：DeltaNet（14 个张量）
A = (H                                   # input_layernorm
     + H*(2*d_k*n_lh + d_v*n_lh)         # in_proj_qkv   = 2048×6144
     + H*(d_v*n_lh)                      # in_proj_z     = 2048×2048
     + 2*H*n_lh                          # in_proj_a + in_proj_b
     + (2*d_k*n_lh + d_v*n_lh)*4         # conv1d（depthwise, k=4）
     + 2*n_lh + d_v                      # A_log, dt_bias, norm
     + H*H                               # out_proj
     + H                                 # post_attention_layernorm
     + 3*H*I)                            # MLP
# 层型 B：全注意力（11 个张量）
B = (H                                   # input_layernorm
     + H*(n_h*d_h*2)                     # q_proj（Q + gate 双宽 = 2048×4096）
     + 2*H*(n_kv*d_h)                    # k_proj + v_proj
     + H*(n_h*d_h)                       # o_proj
     + 2*d_h                             # q_norm + k_norm
     + H                                 # post_attention_layernorm
     + 3*H*I)                            # MLP

text = V*H + L_lin*A + L_attn*B + H      # embed 与 lm_head 共享，只算一次
print(f"A 单层 = {A:,}")                  # 58,814,624
print(f"B 单层 = {B:,}")                  # 52,433,408
print(f"text   = {text/1e9:.4f} B")       # 1.8818

# 视觉塔
vw, vd, vI = 1024, 24, 4096
blk = (3*vw*vw + 3*vw) + (vw*vw + vw) + (vw*vI + vI) + (vI*vw + vw) + 4*vw
vis = (3*2*16*16*vw + vw) + 2304*vw + vd*blk \
      + (2*vw + 4*vw*4*vw + 4*vw + 4*vw*2048 + 2048)
print(f"vis    = {vis/1e9:.4f} B")        # 0.3314

# MTP：一个完整全注意力层 + fc(4096→2048) + 3 个 norm
mtp = B + 2*H*H + 3*H
print(f"mtp    = {mtp/1e9:.4f} B")        # 0.0608

tot = text + vis + mtp
print(f"total  = {tot:,}  ({tot/1e9:.4f} B)")   # 2,274,069,824 / 2.2741 B
# 实测权重文件 4,548,144,832 B ÷ 2 = 2,274,072,416，差 2,592（0.0001%）
```
