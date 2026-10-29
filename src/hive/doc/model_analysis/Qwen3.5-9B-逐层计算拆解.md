# Qwen3.5-9B 逐层计算拆解

2026-10-29 ｜ 依据 `Qwen/Qwen3.5-9B` 的 `config.json`、权重清单（4 个分片，775 个张量，19,306,216,416 B）、`transformers` 的 `modeling_qwen3_5.py` 与 safetensors header 核对

## 摘要

Qwen3.5-9B 是视觉语言模型，总参数 9,653.10 M：文本主干 8,953.80 M、视觉塔 456.01 M、MTP 头 243.29 M。文本主干 32 层，按 3:1 交替放置 24 层 Gated DeltaNet 和 8 层 Gated Attention，每层挂一份 SwiGLU MLP，hidden 为 4096。

对芯片设计影响最大的几项：

- decode 每 token 读 **8.183 GB** 权重（MXFP8）。310 GB/s 下理论上限 **37.9 token/s**，算上 KV 与状态是 36.8，再按 60–70% 的实际效率折算是 22–26 token/s。相对 0.8B，权重大 10.5 倍，速度慢 10.5 倍，没有任何额外损失。
- DeltaNet 的循环状态不再是常数。value 头从 16 涨到 32、层数从 24 涨到 32，FP32 状态合计 **50.33 MB**，是 0.8B/2B 的 2.67 倍。单层 2 MiB 还能驻 SRAM，24 层全量只能落 DRAM，每 token 100.7 MB 流量。
- 每 token **7.976 GMAC**（15.95 GFLOP），算术强度 0.975 MAC/B，与 0.8B 的 0.99、2B 的 0.98 基本相同。规模变了，roofline 上的位置没变：引擎吞吐下限三档都是约 300 GMAC/s。
- lm_head 是 **1.017 GMAC / 1.049 GB**，占 12.8% MAC 与 12.8% 带宽。家族里第一次不再是首位成本（0.8B 是 33.8%，2B 是 27%）。但词表有两份不共享的矩阵，占容量 21.1%。
- KV 每 token **32,768 B**（8 层 × 4 个 KV 头），是家族另外两档的 2.67 倍；256K 上下文 8.59 GB。权重流量追上 KV 要 250K 上下文，是家族里唯一"权重几乎永远主导"的一档。
- prefill 每 token 6.959 GMAC，1024 token 在 25 TOPS 上要 **573 ms**，是 0.8B 的 13.6 倍。TTFT 第一次成为 9B 的主要体验指标。
- 视觉塔只占参数 4.7%，但一张 448² 图要 371.53 GMAC，算术强度 790 MAC/B。1 个视觉 token 的算力只等于 0.27 个文本 token——图在 9B 上反而变便宜了。

后面按"模型事实、层结构、逐算子、算力与带宽、硬件映射、量化、结论"展开。

---

## 目录

- 摘要

**第一部分　事实与全景**
- 第 1 章　事实基线
- 第 2 章　模型全景

**第二部分　结构：每一层用了什么**
- 第 3 章　层清单（32 层逐层）
- 第 4 章　四种层型的子模块清单

**第三部分　计算：每个算子做什么**
- 第 5 章　通用算子库
- 第 6 章　四种层型的逐算子拆解
- 第 7 章　视觉塔与输出头

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

## 1.1 数据来源与核对方法

仓库是 `Qwen/Qwen3.5-9B`（hf-mirror 镜像直取）。`Qwen3.5-9B-Instruct` 与 `Qwen3.5-9B-A3B` 两个变体都返回 404，所以本文全部数字来自 `Qwen3.5-9B` 这一份。权重是 4 个分片，`model.safetensors.index.json` 报 `total_size = 19,306,216,416`，与本文逐张量算出的字节数逐位相同。

核对有两条独立路径。

第一条：header 里 775 个张量的元素数之和是 **9,653,104,368**。权重文件 19,306,216,416 B ÷ 2（除 48 个 FP32 张量外全是 BF16）得 9,653,108,208，差 **3,840** 个参数，占 0.00004%。这个差额不是对齐：24 层的 `A_log`（32 个）与 `norm`（128 个）是 FP32，共 3,840 个元素，每个在文件里比 BF16 多占 2 B，合计 7,680 B；把这 7,680 B 从总量里扣掉再 ÷2，正好是 9,653,104,368。**差额来源已定位到具体张量**，不是漏算，也不是 header 对齐。

第二条：逐层求和。24 × 218,407,104 + 8 × 209,723,904 + 2 × 1,017,118,720 + 4,096 = 8,953,803,264，与 header 里 `model.language_model.*` 的分组统计逐位相符；视觉塔 456,010,480、MTP 243,290,624 同样逐位相符。三条路（逐张量、逐层、按前缀分组）互相对上。

引用的源码只有一份：`transformers` 的 `modeling_qwen3_5.py`。具体是 `Qwen3_5GatedDeltaNet.forward`（含 `repeat_interleave`）、`Qwen3_5Attention.forward`（含 `q_proj` 的 `chunk` 与输出门控）、`Qwen3_5VisionPatchMerger.forward`（含 `use_postshuffle_norm` 分支）、`compute_axial_rope_parameters`（视觉 2D RoPE 的频率构造）与 `Qwen3_5ForCausalLM.forward` 里的 `slice_logits_to_keep`。权重文件本体未下载（19.3 GB），所有形状取自 header，不是从 config 推的；config 与 header 在这一份模型上完全一致。

有一处只用 config 无法判断、必须看 header 的地方：`tie_word_embeddings=false`，且 header 里 `lm_head.weight` 与 `model.language_model.embed_tokens.weight` 是两份独立的 [248320, 4096]。这和 0.8B/2B 的共享词表是结构性差异，见 1.3。

## 1.2 关键超参

### 文本主干

| 超参 | 值 | 与 2B 的差异 |
|---|---|---|
| `hidden_size` | 4096 | 2B 是 2048，翻倍 |
| `num_hidden_layers` | 32 | 2B 是 24，多 8 层 |
| `layer_types` | 24 linear + 8 full，full 在 3/7/11/15/19/23/27/31 | 2B 是 18 + 6，间隔仍是 4 |
| `num_attention_heads` × `head_dim` | 16 × 256 = 4096 | 2B 是 8 × 256 = 2048；head_dim 不变 |
| `num_key_value_heads` | 4（GQA 4:1） | 2B 是 2，比例相同、绝对头数翻倍 |
| `attn_output_gate` | true，`q_proj` 输出 8192 维 | 2B 是 4096；都是 2 倍 hidden |
| `k_proj` / `v_proj` | [1024, 4096] | 2B 是 [512, 2048] |
| `o_proj` | [4096, 4096] | 2B 是 [2048, 2048]；输入等于 hidden |
| `intermediate_size` | 12288（= 3.0 × hidden） | 2B 是 6144（= 3.0 × hidden），比例相同 |
| `linear_num_key_heads` | 16 | 相同 |
| `linear_num_value_heads` | **32** | 2B 是 16，翻倍 |
| `linear_key_head_dim` / `value_head_dim` | 128 / 128 | 相同 |
| `linear_conv_kernel_dim` | 4 | 相同 |
| `vocab_size` | 248320 | 相同 |
| `tie_word_embeddings` | **false** | 2B 是 true |
| `rms_norm_eps` | 1e-6 | 相同 |
| `attention_bias` | false | 相同 |
| `mamba_ssm_dtype` | float32 | 相同 |
| `rope_theta` | 1e7 | 相同 |
| `partial_rotary_factor` | 0.25（256 维里只转 64 维） | 相同 |
| `max_position_embeddings` | 262144 | 相同 |
| `mrope_section` | [11, 11, 10]，interleaved | 相同 |
| `mtp_num_hidden_layers` | 1 | 相同 |
| `mtp_use_dedicated_embeddings` | false | 2B 的 config 无此项 |

### 视觉塔

| 超参 | 值 | 与 2B 的差异 |
|---|---|---|
| `depth` | 27 blocks | 2B 是 24 |
| `hidden_size` | 1152 | 2B 是 1024 |
| `intermediate_size` | 4304 | 2B 是 4096；4304 = 16 × 269，269 是质数 |
| `num_heads` | 16 | 相同 |
| head_dim | **72**（1152 / 16） | 2B 是 64；家族里第一次不是 64 |
| `patch_size` / `temporal_patch_size` / `spatial_merge_size` | 16 / 2 / 2 | 相同 |
| `num_position_embeddings` | 2304（48×48） | 相同 |
| `out_hidden_size` | 4096 | 2B 是 2048 |
| `hidden_act` | gelu_pytorch_tanh | 相同 |
| `deepstack_visual_indexes` | 空 | 相同 |
| `rope_type`（视觉） | axial 2D | 相同 |

### 特殊 token

| 项 | 值 | 与 2B 的差异 |
|---|---|---|
| `eos_token_id` | 248044 | 相同 |
| `image_token_id` | 248056 | 相同 |
| `video_token_id` | 248057 | 相同 |
| `vision_start_token_id` / `vision_end_token_id` | 248053 / 248054 | 相同 |

三处要单独说。

**DeltaNet 里有 2:1 的 GQA。** `linear_num_key_heads=16`、`linear_num_value_heads=32`，q 和 k 各 2048 维，v 是 4096 维，`in_proj_qkv` 的输出因此是 8192（header 实测 [8192, 4096]）。源码在进递推之前做 `repeat_interleave(2, dim=2)`，把 q、k 各复制一份给 32 个 value 头。这是家族里第一次出现 key/value 头数不等，直接决定状态大小：32 × 128 × 128 = 524,288 个元素/层，FP32 就是 2 MiB/层。0.8B 与 2B 的 16 个头是 1 MiB/层。

**层数涨到 32，全注意力仍是每 4 层一个。** 3:1 的排布没变，但绝对层数多出 8 层，KV cache 的层数从 6 变 8，叠加 KV 头数从 2 变 4，每 token 的 KV 涨 2.67 倍。

**词表不共享。** `tie_word_embeddings=false`，两份 [248320, 4096] 各 1,017,118,720 个参数，合计 2,034,237,440，占全模型 21.1%。2B 上这份只存一次（508.56 M，27% 的文本主干）。省下的是容量还是别的，见 1.3 与 11.3。

## 1.3 参数量核算

| 组件 | 数量 | 单个参数 | 小计 | 占比 |
|---|---:|---:|---:|---:|
| 层型 A（DeltaNet） | 24 | 218,407,104 | 5,241,770,496 | 54.30% |
| 层型 B（Attention） | 8 | 209,723,904 | 1,677,791,232 | 17.38% |
| `embed_tokens` | 1 | 1,017,118,720 | 1,017,118,720 | 10.54% |
| `lm_head` | 1 | 1,017,118,720 | 1,017,118,720 | 10.54% |
| final norm | 1 | 4,096 | 4,096 | 0.00% |
| **文本主干** | | | **8,953,803,264** | **92.76%** |
| 视觉塔 | 27 blocks | | 456,010,480 | 4.72% |
| MTP 头 | 1 层 | 243,290,624 | 243,290,624 | 2.52% |
| **总计** | | | **9,653,104,368** | **100.00%** |

单层 A 的 218.41 M 拆开是这样：

| # | 张量 | 形状 | dtype | 元素数 | 归属 |
|---:|---|---|---|---:|---|
| 1 | `input_layernorm.weight` | [4096] | BF16 | 4,096 | 骨架 |
| 2 | `linear_attn.in_proj_qkv.weight` | [8192, 4096] | BF16 | 33,554,432 | mixer |
| 3 | `linear_attn.in_proj_z.weight` | [4096, 4096] | BF16 | 16,777,216 | mixer |
| 4 | `linear_attn.in_proj_a.weight` | [32, 4096] | BF16 | 131,072 | mixer |
| 5 | `linear_attn.in_proj_b.weight` | [32, 4096] | BF16 | 131,072 | mixer |
| 6 | `linear_attn.conv1d.weight` | [8192, 1, 4] | BF16 | 32,768 | mixer |
| 7 | `linear_attn.A_log` | [32] | FP32 | 32 | mixer |
| 8 | `linear_attn.dt_bias` | [32] | BF16 | 32 | mixer |
| 9 | `linear_attn.norm.weight` | [128] | FP32 | 128 | mixer |
| 10 | `linear_attn.out_proj.weight` | [4096, 4096] | BF16 | 16,777,216 | mixer |
| 11 | `post_attention_layernorm.weight` | [4096] | BF16 | 4,096 | 骨架 |
| 12 | `mlp.gate_proj.weight` | [12288, 4096] | BF16 | 50,331,648 | MLP |
| 13 | `mlp.up_proj.weight` | [12288, 4096] | BF16 | 50,331,648 | MLP |
| 14 | `mlp.down_proj.weight` | [4096, 12288] | BF16 | 50,331,648 | MLP |
| | **合计** | | | **218,407,104** | |

单层 B 的 209.72 M：

| # | 张量 | 形状 | dtype | 元素数 | 归属 |
|---:|---|---|---|---:|---|
| 1 | `input_layernorm.weight` | [4096] | BF16 | 4,096 | 骨架 |
| 2 | `self_attn.q_proj.weight` | [8192, 4096] | BF16 | 33,554,432 | mixer（Q + gate 双宽） |
| 3 | `self_attn.k_proj.weight` | [1024, 4096] | BF16 | 4,194,304 | mixer（4 × 256） |
| 4 | `self_attn.v_proj.weight` | [1024, 4096] | BF16 | 4,194,304 | mixer |
| 5 | `self_attn.o_proj.weight` | [4096, 4096] | BF16 | 16,777,216 | mixer（16 × 256） |
| 6 | `self_attn.q_norm.weight` | [256] | BF16 | 256 | mixer |
| 7 | `self_attn.k_norm.weight` | [256] | BF16 | 256 | mixer |
| 8 | `post_attention_layernorm.weight` | [4096] | BF16 | 4,096 | 骨架 |
| 9–11 | `mlp.{gate,up,down}_proj.weight` | 同层型 A | BF16 | 150,994,944 | MLP |
| | **合计** | | | **209,723,904** | |

两个层型的参数只差 4.1%（218.41 对 209.72），差别全在 mixer：A 的 `in_proj_qkv + z` 是 50.33 M，B 的 `q + k/v + o` 是 58.72 M。MLP 在两侧都占 69–72%，是主导项。

视觉塔的 456.01 M：27 个 block 各 15,239,504（`qkv` [3456, 1152] 3,981,312 + bias 3,456；`proj` [1152, 1152] 1,327,104 + bias 1,152；`fc1` [4304, 1152] 4,958,208 + bias 4,304；`fc2` [1152, 4304] 4,958,208 + bias 1,152；两个 LN 的 γ/β 共 4,608），合计 411,466,608；`patch_embed` [1152, 3, 2, 16, 16] + bias = 1,770,624；`pos_embed` [2304, 1152] = 2,654,208；`merger` = norm 2,304 + `linear_fc1` [4608, 4608] 21,233,664 + bias 4,608 + `linear_fc2` [4096, 4608] 18,874,368 + bias 4,096，共 40,119,040。

MTP 的 243.29 M = 一个完整的层型 B（209,723,904）+ `mtp.fc` [4096, 8192]（33,554,432）+ 三个 norm（12,288）。MTP 层用的是全注意力，不是 DeltaNet；它自己还有一份 KV cache（4 头 × 256）。

文本主干的 8.954 B 分三块：24 层 DeltaNet 占 58.5%（5.242 B），8 层 attention 占 18.7%（1.678 B），两份词表矩阵占 22.7%（2.034 B）。对照 2B 的 56.3% / 16.7% / 27.0%：层内参数随 hidden² 涨，词表只随 hidden 涨，所以词表占比第一次掉到四分之一以下。

---
---

# 第 2 章　模型全景

## 2.1 三个子模型

```
Qwen3.5-9B ─┬─ 文本主干  8,953.80 M ─┬─ 24 × DeltaNet 层   5,241.77 M
             │                       ├─ 8 × Attention 层   1,677.79 M
             │                       ├─ embed_tokens       1,017.12 M
             │                       └─ lm_head            1,017.12 M（不共享）
             ├─ 视觉塔    456.01 M ─┬─ 27 × ViT block     411.47 M
             │                     ├─ patch_embed          1.77 M
             │                     └─ pos_embed + merger  42.77 M
             └─ MTP 头     243.29 M ─┬─ 1 × Attention 层   209.72 M
                                     └─ fc + 3 norms      33.57 M
```

纯文本、不投机解码的推理要读 24 层 + 8 层 + lm_head + final norm，即 7,936.68 M 参数，MXFP8 下 **8.183 GB/token**。`embed_tokens` 只在查表时读一行（8 KB），`mtp.*` 只在投机解码时读，视觉塔只在有图时读。全模型 9.653 B 参数按 MXFP8 打包是 9.952 GB，这是容量下限。

和 2B 的 2.274 B 相比，参数涨 4.25 倍，但层内权重涨 4.2 倍、词表只涨 2 倍，所以"每 token 要读多少"和"要装多少"这两个数的比值从 1.03 降到 0.82。

## 2.2 文本主干的层型配比

`layer_types` 实测 32 项，每 4 层一组，全注意力落在组尾：

```
索引:  0  1  2  3 | 4  5  6  7 | 8  9 10 11 |12 13 14 15 |16 17 18 19 |20 21 22 23 |24 25 26 27 |28 29 30 31
类型:  L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A | L  L  L  A
                   ↑              ↑              ↑              ↑              ↑              ↑              ↑
              L = linear_attention (Gated DeltaNet)           A = full_attention (Gated Attention)
```

| 层型 | 层数 | 每层参数 | 每层 MAC（decode） | 结构 |
|---|---:|---:|---:|---|
| A　Gated DeltaNet | 24 | 218.41 M | 220.03 M | 4 路融合投影 + 短卷积 + 2:1 头复用的 delta 递推 |
| B　Gated Attention | 8 | 209.72 M | 209.77 M（不含 S 项） | q/k/v 投影 + 部分 RoPE + KV cache |
| C　SwiGLU MLP | 32（每层挂一份） | 151.01 M | 151.01 M | gate/up 双投影 + SiLU + down |

两种层的参数差从 2B 的 12.2% 缩到 4.1%，因为 MLP 在两侧的占比都接近 70%，mixer 的差别被稀释了。真正拉开差距的还是状态：DeltaNet 每层 2 MiB 定长，attention 每层每 token 4 KB 且随 S 增长。

3:1 的物理意义在 9B 上比 2B 更明显：KV 只落 1/4 的层，256K 上下文时 8.59 GB；若 32 层全是全注意力，这个数会是 34.4 GB。

## 2.3 计算形态的四个类别

模型里所有算子按计算形态只分四类，第 10 章的分类矩阵按同一口径展开：

| 类别 | 特征 | 本模型里的算子 | 典型维度 |
|---|---|---|---|
| 稠密矩阵乘（GEMM / GEMV） | 激活 × 权重 | 所有投影、lm_head、QKᵀ、A·V、视觉塔 GEMM | K 是读入维，N 是输出维，M 是 token 数 |
| 归约 | 沿一维求和或求最值 | RMSNorm、L2 norm、softmax | 4096 / 128 / 72 / S |
| 逐元素 | 同形状逐点 | SiLU、sigmoid、exp、门控乘、残差、RoPE | 任意 |
| 串行递推 | 后一步依赖前一步 | delta 状态更新 | 32 头 × 128×128 |

decode 时所有矩阵乘都退化成 M=1 的 GEMV，prefill 时是 M=S 的 GEMM。外加两个非计算类别：查表（embedding）与搬运（KV cache、`repeat_interleave` 的读放大、量化 pass）。

---
---
# 第二部分　结构：每一层用了什么

---

# 第 3 章　层清单

## 3.1 32 层总表

| 层 | 类型 | 层 | 类型 | 层 | 类型 | 层 | 类型 |
|---:|---|---:|---|---:|---|---:|---|
| 0 | DeltaNet | 8 | DeltaNet | 16 | DeltaNet | 24 | DeltaNet |
| 1 | DeltaNet | 9 | DeltaNet | 17 | DeltaNet | 25 | DeltaNet |
| 2 | DeltaNet | 10 | DeltaNet | 18 | DeltaNet | 26 | DeltaNet |
| 3 | **Attention** | 11 | **Attention** | 19 | **Attention** | 27 | **Attention** |
| 4 | DeltaNet | 12 | DeltaNet | 20 | DeltaNet | 28 | DeltaNet |
| 5 | DeltaNet | 13 | DeltaNet | 21 | DeltaNet | 29 | DeltaNet |
| 6 | DeltaNet | 14 | DeltaNet | 22 | DeltaNet | 30 | DeltaNet |
| 7 | **Attention** | 15 | **Attention** | 23 | **Attention** | 31 | **Attention** |

每层参数：DeltaNet 218.41 M、Attention 209.72 M。24 × 218,407,104 + 8 × 209,723,904 = 6,919,561,728，加上两份词表矩阵 2,034,237,440 和 final norm 4,096，就是文本主干的 8,953,803,264。

## 3.2 层的通用骨架

两种层共用同一个模板，只有中间的 mixer 不同。归一化只读不写，残差加是唯一把结果写回主总线的地方。

```
x [1,4096]
 │
 ├─ 读出 ─► r = input_layernorm(x)  [1,4096]
 │            │
 │            ▼
 │          y = mixer(r)  [1,4096]        ← 层型 A（3.3）/ B（3.4）
 │            │
 └─ x = x + y ◄┘                          ← 残差 ①，唯一写回 x 的点
 │
 ├─ 读出 ─► r = post_attention_layernorm(x)  [1,4096]
 │            │
 │            ▼
 │          z = mlp(r)  [1,4096]          ← 子模块 C（3.5）
 │            │
 └─ x = x + z ◄┘                          ← 残差 ②
 │
输出 x [1,4096]（形状与输入完全相同）
```

**读图约定（第 3 章所有 DAG 通用）**

| 记号 | 含义 |
|---|---|
| `─►` / `──►` | 同 token 内的数据依赖，箭头上带计算 |
| `┄` / `┄┄►` | 纯视图（split / reshape / transpose / repeat_interleave），0 MAC，只改索引与 stride |
| `═` / `══►` | 跨 token 状态读写（`conv_state` / `recurrent_state` / KV cache） |
| `[G0]` `[G1]` … | 并行组：同组内互不依赖，可并发发射；组号小的必须先完成 |
| 横向分支 | 只表示并行关系，不表示时间先后；汇合点之前的所有分支都必须就绪 |

层内只有三种形状变换，其余都是逐元素：

| 变换 | 形状关系 | 例子 | 通道语义 |
|---|---|---|---|
| 稠密线性 | `[K] → [N]` | `in_proj_qkv` 4096→8192、`gate_proj` 4096→12288 | 全部通道混合，每个输出 = 全 K 维内积 |
| 逐元素 / 逐通道 | `[N] → [N]` | RMSNorm 缩放、SiLU、门控乘、残差加 | 一进一出，通道对齐 |
| 纯视图 | `[N] → [M₁]…[Mₖ]`（∏Mᵢ = N） | `split` 8192→2048/2048/4096、`reshape` 4096→32×128、`repeat_interleave` 16→32 头 | 不改数，只改索引 |

维度变化只有两种来源：中间隔了一次 GEMM（宽度等于学出来的读出方向个数），或者只是换了个视图（总元素数不变）。3.3–3.6 的每张图都按这条规则标注；`[G]` 编号在同一层内连续，跨层重新从 `G0` 开始，编号与 0.8B 那篇保持一致，便于逐组对照。

残差流的宽度在整个 32 层中恒为 `[4096]`。这是 2B 的两倍、0.8B 的四倍，意味着每层的残差读写、归一化和逐元素开销也跟着涨，而投影矩阵的 N 维另有自己的规律（3.3、3.4 分别说明）。

## 3.3 层型 A：Gated DeltaNet（24 层）

DeltaNet 把所有历史 token 压缩进一个固定的 `128×128` 状态矩阵，每来一个新 token 更新一次。9B 的新东西是 key/value 头数不等：q、k 各 16 个头，v 有 32 个头，进递推前 q、k 各复制一份。

子模块共 9 个，全部挂在 `linear_attn.*` 下：

| 张量 | 形状 | 作用 |
|---|---|---|
| `in_proj_qkv` | [8192, 4096] | 一次投影出 q（2048）、k（2048）、v（4096），拼在一起 |
| `in_proj_z` | [4096, 4096] | 输出门控 z（进 RMSNormGated 当 gate） |
| `in_proj_b` | [32, 4096] | 每个 value 头一个 β（写入门控） |
| `in_proj_a` | [32, 4096] | 每个 value 头一个 a（衰减率） |
| `conv1d` | [8192, 1, 4] | 深度可分离因果卷积，作用在拼好的 qkv 上，卷积后接 SiLU |
| `A_log` | [32] | 衰减率的可学习基（Mamba 式离散化），FP32 |
| `dt_bias` | [32] | 同上 |
| `norm` | [128] | `RMSNormGated`，按 value 头归一后乘 `silu(z)`，FP32 |
| `out_proj` | [4096, 4096] | 输出投影，把 32 个 value 头混回 4096 |
| `input_layernorm` / `post_attention_layernorm` | [4096] | 骨架自带 |

独有的三个算子：

1. **融合 QKV 投影**：q/k/v 是一个矩阵一次算出来的，DMA 侧要按 2048/2048/4096 切分。三段不等宽，切分模板不能按等分写。
2. **短因果卷积 + SiLU**：跨 token 的局部感受野，需要保存前 3 个 token 的 qkv（192 KB 滚动缓存/层）。
3. **门控 delta 递推**：`S ← S·diag(exp(g)) + β·kᵀ(v − S·k)`，每 token 更新一次全状态，32 个头各一个 128×128。

### 3.3.1 计算流（decode 单 token，依赖 DAG）

```
x [1,4096] ──► [G0] input_layernorm ──► r [1,4096]
                                          │
      ┌──────────────┬────────────────────┼────────────────────┐
      ▼              ▼                    ▼                    ▼
 [G1] in_proj_qkv  [G1] in_proj_a    [G1] in_proj_b       [G1] in_proj_z ───┐
      [1,8192]      [1,32]             [1,32]               [1,4096]        │
      │              │                    │                                 │
      ▼              ▼                    ▼                                 │
 [G2] conv1d+SiLU  g = −exp(A_log)·    β = σ(b)                             │
      [1,8192]      softplus(a+dt)      [1,32]                              │
      │ ═conv_state  [1,32]                │                                 │
      ▼                │                  │                                 │
 [G3] split ┄          │                  │                                 │
  q,k [1,16,128]       │                  │                                 │
  v   [1,32,128]       │                  │                                 │
      │                │                  │                                 │
      ▼                │                  │                                 │
 [G4] L2norm(q),       │                  │                                 │
      L2norm(k)        │                  │                                 │
      repeat ×2 ┄      │                  │                                 │
      q̂,k̂ → [1,32,128]  │                  │                                 │
      └────────────────┴──────────────────┘                                 │
                       ▼                                                    │
 [G5] gated delta rule ═ recurrent_state [32,128,128]                       │
      q̂,k̂,v,β,g ──► o_core [1,32,128]                                       │
                       │                                                    │
                       ▼                                                    │
 [G6] RMSNormGated(o_core, z) ◄──────────────────────────────────────────────┘
                       │ [1,4096]
                       ▼
 [G7] out_proj ──► y [1,4096]
                       ▼
 [G8] x = x + y ──► x [1,4096]
                       ▼
 [G9] post_attention_layernorm ──► r2 [1,4096]
                       │
        ┌──────────────┴──────────────┐
        ▼                             ▼
 [G10] gate_proj [1,12288]      [G10] up_proj [1,12288]
        │                             │
        └──────────────┬──────────────┘
                       ▼
 [G11] silu(g) ⊙ u ──► h [1,12288]
                       ▼
 [G12] down_proj ──► z2 [1,4096]
                       ▼
 [G13] x = x + z2 ──► x [1,4096]
```

### 3.3.2 逐算子映射表

| 组 | 算子 | 形状（in → out） | 数值变换 | MAC/token | 依赖 |
|---|---|---|---|---|---|
| G0 | input_layernorm | `[1,4096] → [1,4096]` | `y_i = x_i·rsqrt(mean(x²)+1e-6)·(1+w_i)` | 8,192 | x |
| G1 | in_proj_qkv | `[1,4096] → [1,8192]` | `qkv_o = Σ_i W[o,i]·r_i` | 33,554,432 | G0 |
| G1 | in_proj_z | `[1,4096] → [1,4096]` | `z_o = Σ_i W_z[o,i]·r_i` | 16,777,216 | G0 |
| G1 | in_proj_a | `[1,4096] → [1,32]` | `a_h = Σ_i W_a[h,i]·r_i` | 131,072 | G0 |
| G1 | in_proj_b | `[1,4096] → [1,32]` | `b_h = Σ_i W_b[h,i]·r_i` | 131,072 | G0 |
| G2 | 因果卷积 + SiLU | `[1,8192] + state[8192,3] → [1,8192]` | `y_c = silu(Σ_{j=0..3} W[c,j]·x_{t+j−3})` | 32,768 | G1(qkv)，`═conv_state` |
| G2 | β | `[1,32] → [1,32]` | `β_h = 1/(1+e^{−b_h})` | 32 | G1(b) |
| G2 | g | `[1,32] → [1,32]` | `g_h = −e^{A_log,h}·ln(1+e^{a_h+dt_h})` | 96 | G1(a)，FP32 |
| G3 | split + reshape | `[1,8192] → [1,16,128]×2 + [1,32,128]` | 纯索引，0 MAC | 0 | G2 |
| G4 | L2 norm(q), L2 norm(k) | `[1,16,128] ×2 → 同形` | `x̂ = x/√(Σx²+1e-6)`，再乘 `d^{−1/2}` | 8,192 | G3 |
| G4 | repeat_interleave ×2 | `[1,16,128] → [1,32,128]` | 纯索引，同一个 k 被两个 v 头共用 | 0 | G4 |
| G5 | gated delta rule | `q̂,k̂,v,β,g → [1,32,128]` | `S ← S·diag(e^g) + β·kᵀ(v − S·k)`；`o = S·q̂` | 1,572,864 | G4, G3(v), G2(β,g)，`═recurrent_state` |
| G6 | RMSNormGated | `[1,32,128] × [1,32,128] → [1,4096]` | 每头 128 维 `RMSNorm(x)·w ⊙ silu(z)` | 16,384 | G5, G1(z) |
| G7 | out_proj | `[1,4096] → [1,4096]` | `y_o = Σ_i W_out[o,i]·c_i`，跨头混合 | 16,777,216 | G6 |
| G8 | 残差加 ① | `[1,4096]` | `x = x + y` | 4,096 | G7 |
| G9 | post_attention_layernorm | `[1,4096] → [1,4096]` | 同 G0 | 8,192 | G8 |
| G10 | gate_proj | `[1,4096] → [1,12288]` | `g = W_g·r2` | 50,331,648 | G9 |
| G10 | up_proj | `[1,4096] → [1,12288]` | `u = W_u·r2` | 50,331,648 | G9 |
| G11 | silu(g) ⊙ u | `[1,12288]` | `h_i = silu(g_i)·u_i` | 12,288 | G10 |
| G12 | down_proj | `[1,12288] → [1,4096]` | `z2 = W_d·h` | 50,331,648 | G11 |
| G13 | 残差加 ② | `[1,4096]` | `x = x + z2` | 4,096 | G12 |

表内 20 行的 MAC 合计 220,033,152。

### 3.3.3 关键路径与并行机会

- 关键路径：`G0 → G1(qkv) → G2(conv) → G3 → G4 → G5 → G6 → G7 → G8 → G9 → G10 → G11 → G12 → G13`。
- `z`、`a`、`b` 三条支路不延长路径，但 `a`/`b` 的产物喂给 G5，必须与 G1 同批发射。它们是 32 维的 GEMV，单独做等于白占一次发射；建议与 `in_proj_qkv` 合并成一次 8256 维输出的投影（取整到 64）。
- 单层 220.03 M MAC 里 GEMM 占 **99.2%**，递推与逐元素占 0.8%。其中 delta 递推一项占 0.7%。
- 延迟上恰恰是这 0.8% 决定下限：delta 的 5 个 pass 依赖前一步的状态，不能展开；`repeat_interleave` 又让 q、k 的每个值被两个 v 头各读一次，读放大量是 2 倍（4 MB 变成 8 MB 的片上读，不落 DRAM）。
- `conv_state` 是 48 KB/层，`recurrent_state` 是 2 MiB/层，24 层合计 48 MiB，片上放不下，见 8.3。跨 token 的只有三处：conv、delta 递推、状态跨 24 层串行；前两处是访存，第三处是调度。

## 3.4 层型 B：Gated Attention（8 层）

Mixer 子模块 6 个，加上骨架自带 2 个：

| 张量 | 形状 | 作用 |
|---|---|---|
| `q_proj` | [8192, 4096] | 双宽：每头 512 = query 256 + 输出门控 256，16 头共 8192 |
| `k_proj` | [1024, 4096] | 4 个 KV 头 × 256 |
| `v_proj` | [1024, 4096] | 同上 |
| `o_proj` | [4096, 4096] | 输出投影（在门控之后），输入等于 hidden |
| `q_norm` | [256] | 按 head_dim 归一，不是对整个 4096 |
| `k_norm` | [256] | 同上 |
| `input_layernorm` / `post_attention_layernorm` | [4096] | 骨架自带 |

和 DeltaNet 相比多出三个算子：

1. 部分 RoPE：只转 256 维中的前 64 维（`partial_rotary_factor=0.25`），其余 192 维直接透传。
2. 因果 softmax：沿 S 维归约，FP32。
3. KV cache：每层每 token 写 2 × 4 × 256 × 2 = 4 KB（FP16）。

### 3.4.1 计算流（decode 单 token，依赖 DAG）

```
x [1,4096] ──► [G0] input_layernorm ──► r [1,4096]
                                          │
      ┌──────────────┬────────────────────┴────────────┐
      ▼              ▼                                 ▼
 [G1] q_proj     [G1] k_proj                      [G1] v_proj
      [1,8192]      [1,1024]                          [1,1024]
      │              │                                 │
      │ view(16,512)+chunk → q [1,16,256]              ▼
      │              │                          [G2] 写 V cache
      ▼              ▼                          v_cache [1,4,S,256]
 [G2] q_norm    [G2] k_norm [1,4,256]            （供 G6）
      ▼              ▼
 [G3] RoPE      [G3] RoPE(64/256)
      │              ▼
      │         [G3] 写 K cache
      │         k_cache [1,4,S,256]（供 G4）
      │              │
      ▼              ▼
 [G4] Q·Kᵀ ◄════════┘
      │  s [1,16,1,S]
      ▼
 [G5] +因果 mask, softmax(FP32)
      │  p [1,16,1,S]
      ▼
 [G6] A·V ◄═ v_cache
      │  o [1,16,1,256] → [1,1,4096]
      ▼
 [G7] o ⊙ σ(gate) ◄── gate [1,4096]（G1 产出，全层驻留）
      │  [1,4096]
      ▼
 [G8] o_proj ──► y [1,4096]
      ▼
 [G9] x = x + y ──► x [1,4096]
      ▼
 [G10] post_attention_layernorm ──► r2 [1,4096] ──► MLP（3.5）
```

`q_proj` 的 8192 维在 G1 内就地拆成两支：`q [1,16,256]` 进 G2；`gate [1,16,256]` 是旁路，一直驻留到 G7 才参与门控。门控向量与残差流等宽（4096），不再是 0.8B 上"门控比残差流还宽"的形态。

### 3.4.2 逐算子映射表

| 组 | 算子 | 形状（in → out） | 数值变换 | MAC/token | 依赖 |
|---|---|---|---|---|---|
| G0 | input_layernorm | `[1,4096] → [1,4096]` | `x·rsqrt(mean(x²)+1e-6)·(1+w)` | 8,192 | x |
| G1 | q_proj | `[1,4096] → [1,8192]` | `y = W_q·r`；`view(…,16,512)` 后 `chunk(2)` → q `[16,256]`、gate `[16,256]` | 33,554,432 | G0 |
| G1 | k_proj / v_proj | `[1,4096] → [1,1024]` | `k = W_k·r`、`v = W_v·r`；`view(…,4,256)` | 8,388,608 | G0 |
| G2 | q_norm / k_norm | q `[1,16,256]`、k `[1,4,256]` 不变 | 每头 256 维 `x·rsqrt(mean₂₅₆(x²)+1e-6)·(1+w)` | 10,240 | G1 |
| G3 | 部分 RoPE | 形状不变 | 前 64 维 half-split 旋转，后 192 维透传 | 2,560 | G2 |
| G3 | K cache 追加 | k → `[1,4,S+1,256]` | 纯搬运，K 已 norm、已旋转 | 0 | G3 |
| G2 | V cache 追加 | v → `[1,4,S+1,256]` | 纯搬运，V 未 norm、未旋转 | 0 | G1 |
| G4 | Q·Kᵀ | q `[1,16,1,256]` × k `[1,16,S,256]` → s `[1,16,1,S]` | `s = α·Σ_{d=0..255} q_d·k_d`，α = 1/16 | 4,096·S | G3 |
| G5 | 因果 mask + softmax | s → p `[1,16,1,S]` | `p = exp(s−m)/Σexp(s−m)`，FP32 | 80·S | G4 |
| G6 | A·V | p × v → `[1,16,1,256]` → `[1,1,4096]` | `o = Σ_{t=1..S} p_t·v_t` | 4,096·S | G5, G2(v) |
| G7 | 输出门控 | `[1,4096] × [1,4096] → [1,4096]` | `o_i ← o_i·σ(g_i)`，在 o_proj 之前 | 8,192 | G6, G1(gate) |
| G8 | o_proj | `[1,4096] → [1,4096]` | `y_o = Σ_i W_o[o,i]·c_i`，跨头混合 | 16,777,216 | G7 |
| G9 | 残差加 ① | `[1,4096]` | `x = x + y` | 4,096 | G8 |
| G10 | post_attention_layernorm | `[1,4096] → [1,4096]` | 同 G0 | 8,192 | G9 |

表内 14 行的固定项合计 209,773,056；注意力项另算 4,096·S（QKᵀ）+ 80·S（softmax）+ 4,096·S（A·V）。

### 3.4.3 关键路径与并行机会

- 关键路径：`G0 → q_proj → q_norm → RoPE → Q·Kᵀ → softmax → A·V → 门控 → o_proj → 残差`。
- 三条并行支路：Q 支路（norm→RoPE）、K 支路（norm→RoPE→写 cache）、V 支路（直接写 cache）。K/V 不占关键路径，它们的代价体现在 G4/G6 的读取上。
- gate 的生命周期最长：G1 产出、G7 消费，中间跨 3 次矩阵乘和 softmax。每 token 8 KB 必须全层驻留。
- 有几处顺序是定义性的，不能重排：`q_norm` 在 transpose 之前，RoPE 在 KV 写入之前，门控在 `o_proj` 之前。
- `repeat_kv` 是 stride-0 的逻辑视图，16 个 Q 头共享 4 个 KV 头（4:1）。硬件若按 16 头各读一份 KV，G4/G6 的 KV 带宽直接变 4 倍：S=256K 时从 8.59 GB 变成 34.4 GB。
- G5 是层内唯一逐行全 S 归约加超越函数。S=4096 时 8 层合计 **524,288 次 exp**，比同层所有 GEMM 的和还慢，是 attention 的延迟核心。
- decode 时 G4/G6 是 M=16、K=S 的 GEMV，算力利用率不到 10%，受 KV 带宽支配；prefill 时它们变成 `S×S×256` 的 GEMM，整层形态完全不同。

## 3.5 子模块 C：SwiGLU MLP（32 层各挂一份）

| 张量 | 形状 | 作用 |
|---|---|---|
| `mlp.gate_proj` | [12288, 4096] | 门控分支 |
| `mlp.up_proj` | [12288, 4096] | 值分支 |
| `mlp.down_proj` | [4096, 12288] | 回投到 4096 |

计算：`y = down( silu(gate(x)) ⊙ up(x) )`

32 层完全同构。它是全模型最大的一块：150.99 M 参数/层 × 32 = 4,831,838,208（算上 393,216 次 SiLU 是 4,832,231,424 MAC），占文本主干参数 54.0%、占全模型 50.1%；算力占每 token 的 60.6%。中间维 12288 正好是 hidden 的 3 倍，与 0.8B（3584 = 3.5×1024）、2B（6144 = 3.0×2048）相比，9B 回到了 3.0 的整数比。

```
r [1,4096] ──► [M1] post_attention_layernorm ──► r' [1,4096]
                                                    │
                 ┌──────────────────────────────────┴───────────────┐
                 ▼                                                  ▼
        [M2a] gate_proj [1,12288]                        [M2b] up_proj [1,12288]
                 │                                                  │
                 ▼                                                  │
        [M3] silu(g) [1,12288]                                     │
                 │                                                  │
                 └──────────────────┬───────────────────────────────┘
                                    ▼
                        [M4] h = silu(g) ⊙ u [1,12288]
                                    ▼
                        [M5] down_proj ──► z [1,4096]
                                    ▼
                        [M6] x = x + z ──► x [1,4096]
```

M2a 与 M2b 共用同一个 4096 维输入，是层内唯一可并行的两支，各 50.33 M MAC。M3 每层 12,288 次 SiLU，32 层合计 393,216 次；加上 DeltaNet 卷积后的 196,608 次，全 token 的 SiLU 是 589,824 次。M5 的 K 维是 12288，是层内最长的归约链（12288 个乘加串成一条链，若按 128 宽 tile 折叠则是 96 级流水）。

三处与 0.8B 不同的地方：

1. 单层 MLP 权重 155.69 MB（MXFP8），是 0.8B 单层（11.35 MB）的 13.7 倍。DMA 描述符若按 64 KB 一个，一层要 2,432 个，调度器要支持大块链式搬运。
2. down_proj 的 K=12288，累加器在 FP32 下是 4096 × 4 B = 16 KB/token，比 0.8B 的 4 KB 大 4 倍。它是 decode 时片上最大的一块激活。
3. MLP 的算术强度 151.01 M MAC ÷ 155.69 MB = 0.97 MAC/B，与全模型一致。它没有任何"靠复用摊薄带宽"的空间，唯一出路是把 M 做大（prefill 或投机解码）。

## 3.6 层型 D：MTP 头（1 层，仅投机解码）

MTP 由四部分组成：输入融合、一个完整的 Gated Attention 层、输出归一化、共享 lm_head。

| 张量 | 形状 | 作用 |
|---|---|---|
| `mtp.pre_fc_norm_hidden` | [4096] | 归一化上一层的 hidden |
| `mtp.pre_fc_norm_embedding` | [4096] | 归一化上一 token 的 embedding |
| `mtp.fc` | [4096, 8192] | 把两个 4096 维拼起来压回 4096 |
| `mtp.layers.0` | 同层型 B | 完整全注意力层（含 KV cache） |
| `mtp.norm` | [4096] | 输出归一化 |

```
h_prev [1,4096] ─► [D1] pre_fc_norm_hidden ─┐
                                            ├─ concat [1,8192] ─► [D3] fc ─► [1,4096]
e_prev [1,4096] ─► [D2] pre_fc_norm_embedding ┘                             │
                                                                            ▼
                                        [D4] layers.0（全注意力，同 3.4）─► [1,4096]
                                                                            ▼
                                        [D5] mtp.norm ─► 共享 lm_head ─► logits [248320]
```

参数 243,290,624 = 一个完整层型 B（209,723,904）+ `mtp.fc`（33,554,432）+ 三个 norm（12,288）。MTP 层用的是全注意力而不是 DeltaNet，所以它自己也有一份 KV cache：每 token 4,096 B，和 8 个 B 层同价。

关键点：MTP 头没有自己的输出词表，`mtp_use_dedicated_embeddings=false`，草稿 token 的 logits 由主 `lm_head` 出。这意味着每个草稿 token 都要读 1.049 GB 的 lm_head 权重——草稿成本几乎等于一个完整 token 的 1/6，而不是"轻量头"。第 10.3 节会把这条算成投机解码的收益上限。

## 3.7 层间差异速查

| 问题 | 答案 |
|---|---|
| 同类层之间有区别吗 | 没有。24 层 DeltaNet、8 层 Attention、32 个 MLP 各自张量名、形状、算子顺序完全一致 |
| DeltaNet 和 Attention 的本质区别 | 状态传递方式：固定 2 MiB 状态 vs 每 token 4 KB 且增长的 KV cache |
| 位置信息怎么进来 | Attention 层用部分 RoPE（64/256）；DeltaNet 层没有 RoPE，靠 4-tap 卷积和递推隐式携带 |
| 哪些层有跨 token 状态 | 全部 32 层：24 层有 conv_state（48 KB）与 recurrent_state（2 MiB），8 层有 KV cache；MTP 另有一份 KV |
| 层型 A 与层型 B 的参数差 | 218.41 M 对 209.72 M，差 4.1%，全在 mixer（50.33 M 对 58.72 M） |
| 与 0.8B/2B 的层结构有区别吗 | 算子顺序相同，但头几何变了：value 头 16 → 32，KV 头 2 → 4，hidden 1024/2048 → 4096，层数 24 → 32 |
| 与 0.8B 的 MLP 比例有区别吗 | 0.8B 是 3.5×hidden（3584/1024），9B 是 3.0×（12288/4096）；MLP 占单层参数从 51% 升到 69% |
| 词表共享吗 | 不共享。`tie_word_embeddings=false`，两份 [248320,4096] |

## 3.8 单层内部张量流（以 layer 0 为例）

这一节给硬件和测试用，逐行追踪 layer 0 的形状变化（decode，S 为当前上下文）。`[G]` 编号与 3.3.1 对应，`┄` 表示纯视图或读放大，`═` 表示跨 token 状态。

```
x                        [1, 4096]          ← 残差流
│
├ [G0] input_layernorm ► r  [1, 4096]                      归约 4096
│
├ [G1] in_proj_qkv ─────► qkv [1, 8192]                    GEMM 4096×8192  ┐
├ [G1] in_proj_z ───────► z   [1, 4096]                    GEMM 4096×4096  │ 四路
├ [G1] in_proj_a ───────► a   [1, 32]                      GEMM 4096×32    │ 并行
├ [G1] in_proj_b ───────► b   [1, 32]                      GEMM 4096×32    ┘
│
├ [G2] conv1d(+SiLU) ───► qkv' [1, 8192]                   深度卷积 4 taps
│     └ ═conv_state [8192, 3]  ← 48 KB
├ [G2] β = sigmoid(b)  [32]                               逐元素
├ [G2] g = −exp(A_log) · softplus(a + dt_bias)  [32]       FP32
│
├ [G3] split ┄──────────► q,k [1,16,128]、v [1,32,128]     0 MAC（纯视图）
│
├ [G4] L2 norm(q), L2 norm(k)                             归约 128 ×32
├ [G4] repeat_interleave ┄─► q̂,k̂ [1,32,128]               读放大 2×
│
├ [G5] gated delta rule ─► o_core [1, 32, 128]             3 × 16,384 MAC/头
│     └ ═recurrent_state [32, 128, 128]  ← 2,097,152 B
│
├ [G6] RMSNormGated(o_core, z) ─► [1, 4096]                归约 128 ×32 + silu
├ [G7] out_proj ────────────► y [1, 4096]                  GEMM 4096×4096
│
x = x + y  [G8]             [1, 4096]                      逐元素
│
├ [G9] post_attention_layernorm ► r2 [1, 4096]             归约 4096
├ [G10] gate_proj ──────► g [1, 12288]                     GEMM 4096×12288  ┐并行
├ [G10] up_proj ────────► u [1, 12288]                     GEMM 4096×12288  ┘
├ [G11] silu(g) ⊙ u ────► h [1, 12288]                    逐元素 + exp
├ [G12] down_proj ──────► z2 [1, 4096]                     GEMM 12288×4096
│
x = x + z2  [G13]           [1, 4096]                      逐元素
```

**并行结构速查（decode）**

| 并行组 | 内容 | 性质 |
|---|---|---|
| G0 | `input_layernorm` | 单点，4096 宽归约（12 级） |
| G1 | `in_proj_qkv` / `in_proj_z` / `in_proj_a` / `in_proj_b` | 四路同时读同一个 r，互不依赖 |
| G2 | `conv1d` / `β` / `g` | conv 等 qkv，β 等 b，g 等 a，三者可并发 |
| G3 | `split` | 纯视图，依赖 conv 输出 |
| G4 | `L2 norm(q)`、`L2 norm(k)`、`repeat` | 两条 16 头归约可并发；repeat 是纯索引 |
| G5 | `gated delta rule` | 汇合点：等 q̂/k̂、v、β/g；输出用更新后的状态 |
| G6 | `RMSNormGated` | 汇合点：等 o_core 与 z（G1 存活最久，8 KB） |
| G7–G13 | `out_proj` → 残差 → 后置归一 → `gate/up` → SwiGLU → `down` → 残差 | 只有 G10 两路并行，其余严格串行 |

对硬件和测试最直接的三点：

1. 关键路径 `G0 → G1(qkv) → G2(conv) → G3 → G4 → G5 → G6 → G7 → G8 → G9 → G10 → G11 → G12 → G13` 上，G5 的 3 个 pass 是唯一的定长串行段：每 pass 16,384 MAC × 32 头 = 524,288 MAC，若递推单元每拍 256 MAC 则 2,048 拍/pass，3 pass 共 6,144 拍/层，24 层 147,456 拍。在 1 GHz 下是 **147 µs**，占 decode 26.4 ms 的 0.56%。
2. 测试向量的切分点就在 `[G]` 边界。组间是完整张量落盘点，组内是并行算子。per-op 对拍时按 G0–G13 各存一份参考值，能定位到具体算子。
3. 只有 G2 与 G5 跨 token。跨 token 状态（48 KB conv + 2 MiB recurrent）只在这两处读写，也是层内仅有的两个无法靠并行隐藏的点。

---
---

# 第 4 章　四种层型的子模块清单（对照用）

这一章把权重按张量列全，用于 DMA 切分、容量核算和测试对拍。形状取自 safetensors header，不是从 config 推的。四种层型是 A（DeltaNet，24 份）、B（Attention，8 份）、C（MLP，32 份）、D（MTP，1 份）。

## 4.1 层型 A 的 14 个张量（24 份，完全一致）

| # | 张量 | 形状 | dtype | 元素/层 | 24 层合计 | 归属 |
|---:|---|---|---|---:|---:|---|
| 1 | `input_layernorm.weight` | [4096] | BF16 | 4,096 | 98,304 | 骨架 |
| 2 | `linear_attn.in_proj_qkv.weight` | [8192, 4096] | BF16 | 33,554,432 | 805,306,368 | mixer |
| 3 | `linear_attn.in_proj_z.weight` | [4096, 4096] | BF16 | 16,777,216 | 402,653,184 | mixer |
| 4 | `linear_attn.in_proj_a.weight` | [32, 4096] | BF16 | 131,072 | 3,145,728 | mixer |
| 5 | `linear_attn.in_proj_b.weight` | [32, 4096] | BF16 | 131,072 | 3,145,728 | mixer |
| 6 | `linear_attn.conv1d.weight` | [8192, 1, 4] | BF16 | 32,768 | 786,432 | mixer |
| 7 | `linear_attn.A_log` | [32] | FP32 | 32 | 768 | mixer |
| 8 | `linear_attn.dt_bias` | [32] | BF16 | 32 | 768 | mixer |
| 9 | `linear_attn.norm.weight` | [128] | FP32 | 128 | 3,072 | mixer |
| 10 | `linear_attn.out_proj.weight` | [4096, 4096] | BF16 | 16,777,216 | 402,653,184 | mixer |
| 11 | `post_attention_layernorm.weight` | [4096] | BF16 | 4,096 | 98,304 | 骨架 |
| 12 | `mlp.gate_proj.weight` | [12288, 4096] | BF16 | 50,331,648 | 1,207,959,552 | MLP |
| 13 | `mlp.up_proj.weight` | [12288, 4096] | BF16 | 50,331,648 | 1,207,959,552 | MLP |
| 14 | `mlp.down_proj.weight` | [4096, 12288] | BF16 | 50,331,648 | 1,207,959,552 | MLP |
| | **合计** | | | **218,407,104** | **5,241,770,496** | |

全部无 bias。三个 FP32 张量（`A_log`、`norm`，以及运行时状态）是不能量化的一块飞地：24 层合计只有 3,840 个元素，但决定了 24 个递推头的数值行为。`conv1d` 是 `[8192, 1, 4]`，group=8192 的真 depthwise，不是普通卷积。

## 4.2 层型 B 的 11 个张量（8 份，完全一致）

| # | 张量 | 形状 | dtype | 元素/层 | 8 层合计 | 备注 |
|---:|---|---|---|---:|---:|---|
| 1 | `input_layernorm.weight` | [4096] | BF16 | 4,096 | 32,768 | |
| 2 | `self_attn.q_proj.weight` | [8192, 4096] | BF16 | 33,554,432 | 268,435,456 | Q 与 gate 双宽 |
| 3 | `self_attn.k_proj.weight` | [1024, 4096] | BF16 | 4,194,304 | 33,554,432 | 4 × 256 |
| 4 | `self_attn.v_proj.weight` | [1024, 4096] | BF16 | 4,194,304 | 33,554,432 | |
| 5 | `self_attn.o_proj.weight` | [4096, 4096] | BF16 | 16,777,216 | 134,217,728 | 16 × 256 |
| 6 | `self_attn.q_norm.weight` | [256] | BF16 | 256 | 2,048 | 按 head_dim |
| 7 | `self_attn.k_norm.weight` | [256] | BF16 | 256 | 2,048 | 按 head_dim |
| 8 | `post_attention_layernorm.weight` | [4096] | BF16 | 4,096 | 32,768 | |
| 9 | `mlp.gate_proj.weight` | [12288, 4096] | BF16 | 50,331,648 | 402,653,184 | |
| 10 | `mlp.up_proj.weight` | [12288, 4096] | BF16 | 50,331,648 | 402,653,184 | |
| 11 | `mlp.down_proj.weight` | [4096, 12288] | BF16 | 50,331,648 | 402,653,184 | |
| | **合计** | | | **209,723,904** | **1,677,791,232** | |

`q_proj` 是 2 倍 hidden、`o_proj` 的输入等于 hidden——这两处随 hidden 线性缩放，所以 9B 上 q_proj 占本层 16.0%（0.8B 上是 17.1%），不再是"小模型偏贵"的那一项。真正的固定开销只剩 q_norm/k_norm 的 512 个元素。

## 4.3 运行时状态（不在权重里，但决定 SRAM）

| 状态 | 维度 | 精度 | 每层 | 全模型 | 位置 |
|---|---|---|---:|---:|---|
| `recurrent_state` | [32, 128, 128] | FP32 | 2,097,152 B | 50,331,648 B | 单层可驻 SRAM，24 层需 DRAM |
| `conv_state` | [8192, 3] | FP16 | 49,152 B | 1,179,648 B | SRAM |
| `k_cache` | [4, S, 256] | FP16 | 2,048·S B | 16,384·S B | DRAM |
| `v_cache` | [4, S, 256] | FP16 | 2,048·S B | 16,384·S B | DRAM |
| MTP `k/v cache` | [4, S, 256] ×2 | FP16 | 4,096·S B | 4,096·S B | DRAM，仅投机解码 |

两个数最该记住。DeltaNet 状态 **50.33 MB**，不再是家族里"和模型大小无关的常数"：单头 128×128 没变，但 value 头 16 → 32、层数 24 → 32，乘出 2.67 倍。KV cache 每 token **32,768 B**，也是 2.67 倍（层数 6 → 8，KV 头 2 → 4）。9B 上"状态不随规模变"这条经验第一次失效。

---
---
# 第三部分　计算：每个算子做什么

---

# 第 5 章　通用算子库

全模型只用到 6 个通用算子：RMSNorm、RMSNormGated、SiLU/SwiGLU、因果深度卷积、部分 RoPE、`repeat_interleave`。后面任何一层都是这几个算子的排列组合。

它们都上不了矩阵引擎（`repeat_interleave` 连算术都没有），原因相同：没有 K 维归约加规整访存的结构，要么是行内归约（RMSNorm），要么是跨 token 滑窗（conv），要么是跨 lane 置换（RoPE、repeat）。

三条量化边界需要记住：增益是 `1+w` 而不是 `w`；`silu` 只能留在 FP16 走 LUT；RoPE 角度必须用 FP32 算，256K 上下文下建议 FP64 或整数 mod 2π。

| 算子 | 每 token 次数 | 单次规模 | MAC/次 | 合计 MAC |
|---|---:|---|---:|---:|
| RMSNorm（32 层 × 2 + final） | 65 | 4096 维 | 8,192 | 532,480 |
| q_norm | 8 | 16 头 × 256 | 8,192 | 65,536 |
| k_norm | 8 | 4 头 × 256 | 2,048 | 16,384 |
| RMSNormGated | 24 | 32 头 × 128 | 16,384 | 393,216 |
| SwiGLU 门控乘 | 32 | 12288 维 | 12,288 | 393,216 |
| 因果深度卷积 | 24 | 8192 通道 × 4 tap | 32,768 | 786,432 |
| 部分 RoPE | 8 | 20 头 × 32 对 | 2,560 | 20,480 |
| **合计** | **169** | | | **2,207,744** |

这 2.21 M MAC 只占每 token 7.976 G 的 0.03%，但它们是 169 次独立的 kernel 启动和 169 次流水线气泡。decode 时真正的成本是延迟不是算力：单次 4096 宽归约 12 级流水，169 次里 105 次是归一化，串起来就是 1,260 级无法并行的流水。

## 5.1 RMSNorm（zero-centered，Qwen 特有）

**作用**：按行归一化，再乘一个可学习的逐通道增益。
**公式**：

```
y_i = x_i · rsqrt(mean(x²) + ε) · (1 + w_i),   ε = 1e-6
```

注意增益是 `1 + w`，不是 `w`。初始化时 `w = 0`，等价于恒等缩放。硬件若按标准 RMSNorm 实现（乘 `w`），权重需要预加 1，否则整层输出被压到接近 0。

**形状**：`[1,4096] → [1,4096]`，归约维 4096。
**MAC**：2 × 4096 = 8,192（平方和一次、缩放一次）。32 层 × 2 次 + final 1 次 = 65 次/token，合计 532,480，可以忽略。
**精度**：平方和用 FP32 累加。4096 个 FP16 平方直接相加，最坏情况损失约 3.5 位有效数字（4096 = 2¹²）；归一后的值再降回 FP16。
**坑**：

1. `1+w` 的偏移必须在权重打包时处理。这是 Qwen 系与 Llama 系最大的实现差异，也是移植时最常见的静默错误。final norm 与 64 次层内 norm 是同一个算子，只是没有后续残差，硬件复用同一单元。
2. 4096 宽、12 级归约在 1 GHz 下约 12 拍，乘以 105 次归一化是 1,260 拍；若每层入口都要等它出结果，32 层流水的气泡比这 1,260 拍更贵。归约单元应与矩阵引擎的权重预取并行。

## 5.2 RMSNormGated（DeltaNet 专用）

**作用**：在 DeltaNet 的递推输出上做 per-head 归一，再乘输出门控。
**公式**：

```
o_h = (RMSNorm(o_core_h) · w) ⊙ silu(z_h),   h = 0..31，每头 128 维
```

**形状**：`[1,32,128] × [1,32,128] → [1,4096]`。
**MAC**：每头 3 × 128 = 384（平方和、缩放、门控乘），32 头 12,288，加 silu 的 4,096，共 16,384。
**精度**：归约在 FP32 里做，`w` 是 FP32 权重（header 里 `linear_attn.norm.weight` 就是 FP32）。`silu(z)` 用 FP16 LUT。
**坑**：

1. 归约是按 value 头做的，不是按 4096 整行。32 个 128 宽的归约可以完全并行，但输出必须按头顺序拼回 4096。
2. `w` 只有 128 个值，被 32 个头共用 32 路广播，在 SRAM 里占 512 B。
3. 门控 `z` 来自 `in_proj_z`，在 G1 产生、G6 消费，中间跨整个递推。这是层内生命周期最长的一份数据（8 KB）。
4. 与 0.8B 的差别只在头数（16 → 32）。归约单元若按 16 路设计，这里要跑两趟，延迟翻倍。

## 5.3 SwiGLU / SiLU

**作用**：MLP 的门控激活。`silu(x) = x·sigmoid(x)`。
**公式**：

```
h = silu(W_gate·r) ⊙ (W_up·r)
y = W_down·h
```

**形状**：`[1,4096] → [1,12288] ⊙ [1,12288] → [1,4096]`。
**MAC**：门控乘 12,288/层，32 层 393,216；SiLU 本身也是 12,288 次超越函数/层，32 层 393,216 次 exp。加上卷积后的 196,608 次，全 token 589,824 次 SiLU。
**精度**：SiLU 的负半轴把误差放大（`silu(−8) ≈ −2.7e-3`，而输入误差被 sigmoid 的斜率放大），必须 FP16。FP8 的 E4M3 在负半轴相对误差可达 6%，直接毁掉小激活。
**坑**：

1. `silu` 是层内出现次数第二多的超越函数（第一是 softmax 的 exp，但那只在 8 层）。LUT 按 FP16 索引算，16K 项 × 16 bit = 32 KB。
2. 12288 维是 4096 的 3 倍。逐元素单元按 12288 宽设计，不要按 hidden 设计——这是 9B 上最容易低估的向量单元规格。
3. 589,824 次 SiLU 若走 SFU（每拍 4 个），是 147,456 拍 = 147 µs，与 DeltaNet 递推同量级。它分布在 32 层里可以隐藏，但向量单元吞吐不能低于每拍 4 个。

## 5.4 因果深度卷积 + SiLU（DeltaNet 专用）

**作用**：在 qkv 上做 4-tap 的深度可分离卷积，给 DeltaNet 补一个局部感受野。
**公式**：

```
y_c,t = silu( Σ_{j=0..3} W[c,j] · x_c,t−3+j ),   c = 1..8192，逐通道独立
```

**形状**：`[1,8192] + state[8192,3] → [1,8192]`。
**MAC**：8192 × 4 = 32,768/层/token，24 层 786,432。
**精度**：FP16 足够，`silu` 之后的值域受控。但 conv 权重不要量化到 FP8 的 MX 块结构——它是跨 token 的，块内 32 个元素的时间相关性会被块缩放破坏。
**坑**：

1. 状态是 `[8192,3]`，49,152 B/层。每 token 滚动一次，写回时要把状态整体左移一格，是 96 KB 的读改写（读 48 KB + 写 48 KB），不能只写新值。
2. `conv1d` 权重形状是 `[8192,1,4]`，group = 8192，是真正的 depthwise，不是普通卷积。
3. 它和 delta 递推一样跨 token，是 decode 时仅有的两个状态读写点之一。24 层合计 2.25 MiB/token 的 conv 状态流量。

## 5.5 部分 RoPE（attention 专用）

**作用**：给 Q/K 注入位置信息。只旋转 head_dim=256 的前 64 维，后 192 维原样透传（`partial_rotary_factor=0.25`）。
**公式**：

```
θ_i = pos / 1e7^(i/32),   i = 0..31
q'_i      = q_i      · cos θ_i − q_{i+32} · sin θ_i
q'_{i+32} = q_{i+32} · cos θ_i + q_i      · sin θ_i
q'_j = q_j,   j = 64..255（透传）
```

**形状**：`q [1,16,S,256]`、`k [1,4,S,256]`；`cos/sin [S,64]`（32 个角度被复制成 64 份，所以 `cos[i] == cos[i+32]`）。
**MAC**：每对 4 次乘加。q 是 16 × 32 × 4 = 2,048，k 是 4 × 32 × 4 = 512，合计 2,560/层/token，8 层 20,480。
**精度**：角度在 FP32 里算（`position_ids.float() * inv_freq`），算完再降到模型 dtype。旋转本身 FP16 可接受。
**坑**：

1. half-split 不是 interleaved。`rotate_half` 把向量劈成前后两半再配对：`(q_0, q_32)、(q_1, q_33)…`。按 interleaved 实现不会报错，只会让模型答非所问。
2. `pos` 必须是整数、角度必须 FP32。在 262144 位置上 FP16 的 `pos` ulp 是 0.25，角度误差 0.25 rad，旋转完全失效。FP32 的 `pos` 到 262144 仍然精确，但 `pos/1e7^(i/32)` 必须用 FP32 或更高。
3. cos/sin 表不要预存。32 个角度 × 4 B = 128 B/位置，S=4096 是 512 KB，S=262144 是 32 MB。现算每 token 只有 32 对 sin/cos。
4. mRoPE 在纯文本下是恒等变换：三组 pos 相同，`[11,11,10]` 的交错退化为复制。但支持图像时必须原样实现（32 = 11+11+10，正好用满 64 维的一半）；cos/sin 广播到 20 个头共享，不要每头各算。

## 5.6 `repeat_interleave`（9B 新增）

**作用**：把 16 个 key 头复制成 32 个，让每个 value 头配一个 key 头。源码里是 `q.repeat_interleave(2, dim=2)`、`k.repeat_interleave(2, dim=2)`。
**公式**：纯索引，`q̂[2h] = q̂[2h+1] = q[h]`，`k̂` 同理。
**形状**：`[1,16,128] → [1,32,128]`，q 与 k 各一次。
**MAC**：0。
**精度**：无影响（同一个值）。
**坑**：

1. 它不是"免费的视图"。`repeat_interleave` 在 PyTorch 里会物化成连续张量，16 KB/token/层 的 SRAM 写；硬件应改用 stride-0 的广播描述符，让两个 v 头读同一份 k。
2. 读放大量是 2 倍：q、k 各 4 KB，被 32 个 v 头读成 16 KB。24 层合计 384 KB/token 的片上读，不落 DRAM，但占 SRAM 带宽。
3. 它是 9B 与 0.8B/2B 唯一的结构性算子差异。0.8B/2B 的 key/value 头都是 16，没有这一步；移植时若漏掉，递推形状对不上，报错还算好的，静默广播错才是灾难。递推里的 `S·k` 用复制后的 k，索引必须对齐 value 头。

---
---
# 第 6 章　四种层型的逐算子拆解

这一章把 4 种层型拆成算子卡（A①–A⑰ / B①–B⑫ / M1–M6 / D1–D8）。每张卡的字段固定：形状、MAC、归属、精度、坑。MAC 统计包含逐元素与归约，只排除纯视图算子——这样表的每一行都能直接相加，与第 8 章的总账对上。

两个前提先说清楚。层型 A 的算力 99.2% 在矩阵乘，0.8% 在递推与逐元素，但延迟上那 0.8% 决定一切。层型 B 的成本重心不在权重而在 S：S=4096 时 `QKᵀ` 加 `A·V`（33.55 M）已经和 q/k/v/o 四个投影（41.94 M）同量级，S=32768 时是后者的 6.5 倍。

## 6.1 层型 A：Gated DeltaNet（24 层）

### A① 前置 RMSNorm

- 形状与 MAC：`[1,4096] → [1,4096]`，归约 4096；8,192；归属 VPU。
- 精度与坑：平方和 FP32，输出 FP16；增益 `1+w`；层入口固定 12 级流水；`w` 与 final norm 同构，复用同一单元。

### A② 融合 QKV 投影

- 形状与 MAC：`[1,4096] → [1,8192]`，一次算出 q 2048 + k 2048 + v 4096；33,554,432；归属矩阵引擎，N=8192、K=4096。
- 精度与坑：权重 MXFP8，激活 FP16，累加 FP32；三段不等宽（2048/2048/4096），DMA 与 tile 切分不能按等分写；输出 16 KB 必须整体留在 SRAM 供卷积使用。

### A③ 输出门控投影（z）

- 形状与 MAC：`[1,4096] → [1,4096]`；16,777,216；归属矩阵引擎。
- 精度与坑：同 A②；z 是层内生命周期最长的数据（8 KB），从 G1 驻留到 G6，跨 3 个 pass。片上若放不下，每层多 16 KB 往返。

### A④ a / b 双投影

- 形状与 MAC：`[1,4096] → [1,32]` ×2；131,072 ×2 = 262,144；归属矩阵引擎，但 N=32。
- 精度与坑：a、b 后续进 FP32 超越函数，投影本身可 MXFP8；N=32 在 128×128 的 MMA tile 上只用 25% 列，算力利用率极低；真正代价是两次独立 kernel 启动和 2×132 KB 权重搬运。建议与 A② 拼成 N=8256（取整 8320）一次算完。

### A⑤ 因果卷积 + SiLU

- 形状与 MAC：`[1,8192] + state[8192,3] → [1,8192]`；32,768；归属向量/卷积单元（depthwise，group=8192）。
- 精度与坑：FP16；conv 权重不要进 FP8 的 MX 块；状态 49,152 B/层，滚动时整体左移，是 96 KB 读改写；`silu` 另算 8,192 次超越函数。

### A⑥ β（写入门控）

- 形状与 MAC：`[1,32] → [1,32]`，`β = sigmoid(b)`；32（加 32 次 sigmoid）；归属 VPU。
- 精度与坑：FP32 计算，降到 FP16 参与递推也可，但 β 的误差会被 16,384 个状态元素放大；32 个标量，延迟是一次 5 级流水；它卡在递推入口，不能等整个 A④ 完成后再算，应与 A② 并行发射。

### A⑦ g（衰减）

- 形状与 MAC：`[1,32] → [1,32]`，`g = −exp(A_log) · softplus(a + dt_bias)`；96（exp + log1p + 乘 + 取负）；归属 VPU，FP32。
- 精度与坑：必须 FP32。`softplus` 在 `a+dt` 较大时退化为线性，FP16 会把 `ln(1+e^x)` 的尾数抹掉；`exp(g)` 是 32 个标量，广播到 128×128 状态；g 越接近 0 衰减越慢，数值上要保证 `exp(g) ∈ (0,1]`，任何大于 1 的实现都会让状态发散。

### A⑧ split

- 形状与 MAC：`[1,8192] → q[1,16,128] + k[1,16,128] + v[1,32,128]`；0；归属 DMA/地址生成。
- 精度与坑：无；三段偏移是 0 / 2048 / 4096（按元素），步长不同；不要用一次 `view` 之后按 head 切，q、k、v 的头数不同（16/16/32）。

### A⑨ L2 norm(q)、L2 norm(k)

- 形状与 MAC：`[1,16,128] ×2`，形状不变；8,192（2 × 16 头 × (128 平方 + 128 缩放)）；归属 VPU。
- 精度与坑：FP32 归约；`1/√128` 是常数，可折进 q 的缩放；16 条 128 宽归约可并行，7 级流水；q 与 k 的 norm 互不依赖，应双 lane 并发。k 的 norm 结果会被两个 value 头共用，只算一次。

### A⑩ `repeat_interleave` ×2

- 形状与 MAC：q、k 各 `[1,16,128] → [1,32,128]`；0；归属 DMA/广播网络。
- 精度与坑：无；读放大 2 倍（16 KB/token/层）；不要物化成连续张量，用 stride-0 描述符；索引必须与 32 个 value 头对齐。

### A⑪ gated delta rule

- 形状与 MAC：`q̂,k̂,v,β,g → o_core [1,32,128]`，状态 `[32,128,128]`；1,572,864；归属递推单元，逐头串行 3 个 pass。
- 精度与坑：状态 FP32；q̂/k̂/β/g 可 FP16，`S·k` 的累加必须 FP32；见下方分解，这是全模型唯一不能靠并行隐藏的算子。

三个 pass 的账：

| pass | 运算 | MAC/头 | 依赖 |
|---|---|---:|---|
| 1 | `u = S·k`（128×128 矩阵乘 128 向量） | 16,384 | 读全状态 |
| 2 | `S ← S·exp(g) + β·kᵀ(v − u)`（衰减乘、秩一加、外积累加融合为 FMA） | 16,384 | 等 pass 1 |
| 3 | `o = S·q̂`（用更新后的状态） | 16,384 | 等 pass 2 |
| | **合计/头** | **49,152** | 32 头 × 49,152 = 1,572,864 |

`v − u` 的 128 次减法折进 pass 1 的累加器尾，不计入 MAC。pass 2 的 128×128 个元素每个做一次 `s·e^g + β·k_i·e_j` 的 FMA，衰减与秩一更新在同一拍完成，这是 9B 递推只有 3 个 pass 而不是 5 个的原因。

### A⑫ RMSNormGated

- 形状与 MAC：`[1,32,128] × [1,32,128] → [1,4096]`；16,384（32 头 × 3 × 128，含 4,096 次 silu）；归属 VPU。
- 精度与坑：归约 FP32，`w` FP32（128 值），`silu(z)` FP16 LUT；32 条 128 宽归约；`w` 需 32 路广播；必须等 A⑪ 的 pass 3 与 A③ 的 z 同时就绪。

### A⑬ out_proj

- 形状与 MAC：`[1,4096] → [1,4096]`，把 32 个头混回 4096；16,777,216；归属矩阵引擎。
- 精度与坑：MXFP8 权重，FP32 累加；K=4096 是 32 个头的拼接，跨头混合只能在这里发生；不能提前在头内做部分和再合并，否则语义变了。

### A⑭ 残差加 ①

- 形状与 MAC：`[1,4096]`；4,096；归属 VPU/向量单元。
- 精度与坑：FP16 加法即可，但残差流是累加 32 次的量，建议 FP16 带 FP32 补偿或直接 BF16 累加；这是层内唯一写回主总线的点，写冲突要避免；与 A⑬ 的输出共用同一块 buffer 可省一次搬运。

### A⑮ 后置 RMSNorm

- 形状与 MAC：`[1,4096] → [1,4096]`；8,192；归属 VPU。
- 精度与坑：同 A①；它与 A① 是同一算子，硬件复用；但它在残差之后，必须等 A⑭ 写完。

### A⑯ 单 token 合计与引擎准入

| 分组 | MAC | 占比 |
|---|---:|---:|
| 矩阵乘：qkv + z + a/b + out_proj | 67,371,008 | 30.62% |
| 矩阵乘：MLP gate/up/down | 150,994,944 | 68.63% |
| delta 递推 | 1,572,864 | 0.71% |
| 归一化（4 次） | 40,960 | 0.02% |
| 因果卷积 | 32,768 | 0.01% |
| 逐元素 + 门控 + g/β | 20,608 | 0.01% |
| **合计** | **220,033,152** | **100.00%** |

能上矩阵引擎的只有 8 个投影，合计 218,365,952 MAC（99.24%），权重 225,177,724 B。不能上的部分只有 1.67 M MAC，却包含 3 个串行 pass、4 次归约和 1 次跨 token 状态读改写。

### A⑰ 精度预算与实现坑

| 数据 | 精度 | 理由 |
|---|---|---|
| 权重（8 个投影） | MXFP8 (E4M3 + E8M0 block 32) | 算术强度 0.97 MAC/B，带宽主导，量化收益直接 |
| 激活 r、qkv、z | FP16 | 动态范围够，SiLU 与门控都在 FP16 |
| 递推状态 S | FP32 | 跨 token 累积，FP16 在 32 层 × 数千 token 后漂移 |
| `A_log`、`dt_bias`、`norm.w` | FP32 | 只有 192 个元素，量化收益为零、风险极高 |
| `conv1d` 权重 | FP16 | 跨 token 滑窗，MX 块缩放会破坏时间相关性 |
| 累加器 | FP32 | 4096 长归约，FP16 累加损失约 3.5 位 |

实现与测试坑，按危害排序：

1. **`1+w` 增益**。漏了偏移，整层输出被压到接近 0，而且不会报错。打包权重时统一加 1。
2. **pass 3 用旧状态**。`o = S·q̂` 必须用 pass 2 更新之后的 S。用旧状态不会崩，只是长上下文精度缓慢下降，最难定位。
3. **conv 状态左移**。只写新值不搬历史，等价于把卷积窗口缩到 1，输出仍然"合理"。
4. **`exp(g)` 的数值范围**。g 由 `−exp(A_log)·softplus(...)` 得到，恒为负；实现里若把 `A_log` 当 FP16 读，exp 后可能溢出成 inf，状态立刻 NaN。
5. **L2 norm 的 eps**。`x/√(Σx²+1e-6)` 的 1e-6 在 FP16 下会被舍入掉，除法要放 FP32。
6. **a/b 合并的取整**。拼成 N=8256 时，尾部 64 列是 padding，输出必须丢弃，别把 padding 当第 33 个头。
7. **状态布局**。`[32,128,128]` 是 head-major；若按 `[128,128,32]` 存，pass 1 的 128 点积会跨 4 KB stride，带宽掉一个数量级。

## 6.2 层型 B：Gated Attention（8 层）

### B① 前置 RMSNorm

- 形状与 MAC：`[1,4096] → [1,4096]`；8,192；归属 VPU。
- 精度与坑：平方和 FP32；与 A① 同一单元；入口气泡 12 级。

### B② q_proj（含输出门控）

- 形状与 MAC：`[1,4096] → [1,8192]`，reshape 成 `[16,512]` 后 `chunk(2)`；33,554,432；归属矩阵引擎，N=8192、K=4096。
- 精度与坑：MXFP8 权重，FP32 累加；切分是**逐头**的：每个头 512 = [q 256 | gate 256]。按 8192 的前 4096 / 后 4096 全局切分，会把 8 个头的 q 当成 gate，模型仍能跑，输出全错。

### B③ k_proj / v_proj

- 形状与 MAC：`[1,4096] → [1,1024]` ×2，4 个 KV 头 × 256；4,194,304 ×2 = 8,388,608；归属矩阵引擎。
- 精度与坑：输出要写 KV cache，建议 FP16 落盘；若用 FP8 KV，这里是插入 scale 的点；k 与 v 的头数只有 4，不是 16；`[1024,4096]` 的权重是 q_proj 的 1/8。

### B④ q_norm / k_norm

- 形状与 MAC：q `[1,16,256]`、k `[1,4,256]`，形状不变；10,240（16×256×2 + 4×256×2）；归属 VPU。
- 精度与坑：FP32 归约，`w` 只有 256 个 BF16 值；归约维是 head_dim 256，不是 hidden 4096；q 的 16 条与 k 的 4 条共 20 条 256 宽归约（8 级），q 与 k 可并发。

### B⑤ 部分 RoPE

- 形状与 MAC：`[1,16,256]`、`[1,4,256]`，只转前 64 维；2,560；归属 VPU/置换网络。
- 精度与坑：角度 FP32，旋转 FP16；half-split（(0,32) 配对）；后 192 维透传；cos/sin 广播到 20 个头。

### B⑥ KV cache 追加

- 形状与 MAC：k → `[1,4,S+1,256]`，v → `[1,4,S+1,256]`；0；归属 DMA。
- 精度与坑：FP16（每 token 4,096 B/layer）；K 必须已 norm、已旋转后写入；V 不 norm、不旋转。顺序反了不会报错，只会让长上下文崩。分页写的页大小要与 4 KB/token 对齐。

### B⑦ Q·Kᵀ

- 形状与 MAC：q `[1,16,1,256]` × k `[1,16,S,256]` → `[1,16,1,S]`；4,096·S；归属矩阵引擎（GEMV，M=16）。
- 精度与坑：FP16 乘、FP32 累加；缩放 1/16 在 softmax 前；GQA 4:1，16 个 q 头共享 4 个 kv 头；用 stride-0 广播，别真读 16 份。S=262144 时单层 1.07 GMAC。

### B⑧ 因果 mask + softmax

- 形状与 MAC：`[1,16,1,S]`；80·S（16 头 × 5 pass：max、sub、exp、sum、div）；归属 VPU。
- 精度与坑：FP32 全程；exp 用 SFU；decode 时 S 是全部历史，要在线维护 max/sum；prefill 时 causal mask 让第 t 行只算 t 个，`S(S+1)/2`。S=4096 时 8 层合计 524,288 次 exp，是 attention 的延迟核心。

### B⑨ A·V

- 形状与 MAC：p `[1,16,1,S]` × v `[1,16,S,256]` → `[1,16,1,256]`；4,096·S；归属矩阵引擎（GEMV）。
- 精度与坑：FP16 乘、FP32 累加，输出降 FP16；v 也要按 4:1 广播；与 B⑦ 共用同一份 KV 读，能合并成一次取数就合并。

### B⑩ 输出门控

- 形状与 MAC：`[1,4096] × [1,4096]`；8,192（4,096 sigmoid + 4,096 乘）；归属 VPU。
- 精度与坑：sigmoid FP16 足够（值域 (0,1)）；位置在 o_proj **之前**，不是之后；gate 来自 B② 的后半，跨 3 个算子驻留 8 KB。

### B⑪ o_proj

- 形状与 MAC：`[1,4096] → [1,4096]`；16,777,216；归属矩阵引擎。
- 精度与坑：MXFP8 权重；输入是 16×256=4096，不是 8192；门控已经在 B⑩ 乘完。

### B⑫ 残差与后置归一

- 形状与 MAC：`[1,4096]`；4,096 + 8,192 = 12,288；归属 VPU。
- 精度与坑：同 A⑭/A⑮；写回点唯一；后置归一与 MLP 入口之间不要插额外搬运。

### 6.2.1 层型 B 单 token 合计

| 分组 | MAC | 占比（S=4096） |
|---|---:|---:|
| q/k/v 投影 | 41,943,040 | 17.21% |
| o_proj | 16,777,216 | 6.89% |
| MLP | 151,007,232 | 61.98% |
| 归一化 + RoPE + 门控 | 45,568 | 0.02% |
| QKᵀ + A·V | 33,554,432 | 13.77% |
| softmax | 327,680 | 0.13% |
| **合计** | **243,655,168** | **100.00%** |

不含 S 项的固定部分是 209,773,056。随 S 变化的三项合计 8,272·S（8,192 矩阵 + 80 softmax）：

| S | QKᵀ + A·V | softmax | 单层合计 | ×8 层 |
|---:|---:|---:|---:|---:|
| 4,096 | 33,554,432 | 327,680 | 243,655,168 | 1,949,241,344 |
| 32,768 | 268,435,456 | 2,621,440 | 480,829,952 | 3,846,639,616 |
| 262,144 | 2,147,483,648 | 20,971,520 | 2,378,228,224 | 19,025,825,792 |

### 6.2.2 KV cache 三档

| S | 每层（4 头） | 8 层 | MTP 层 | 全模型含 MTP |
|---:|---:|---:|---:|---:|
| 4,096 | 16,777,216 B | 134,217,728 B | 16,777,216 B | 150,994,944 B |
| 32,768 | 134,217,728 B | 1,073,741,824 B | 134,217,728 B | 1,207,959,552 B |
| 262,144 | 1,073,741,824 B | 8,589,934,592 B | 1,073,741,824 B | 9,663,676,416 B |

每层每 token 4,096 B（k 2,048 + v 2,048）。256K 上下文下 8 层 KV 是 8.59 GB，比整个文本主干的权重（8.183 GB）还大——这是 9B 上权重第一次在长上下文里让位给 KV。

### 6.2.3 本层权重带宽（MXFP8，1.031 B/参数）

| 项 | 参数 | 字节 |
|---|---:|---:|
| q_proj | 33,554,432 | 34,594,619 |
| k_proj + v_proj | 8,388,608 | 8,648,655 |
| o_proj | 16,777,216 | 17,297,310 |
| q_norm + k_norm | 512 | 528 |
| 两个 RMSNorm | 8,192 | 8,446 |
| MLP | 150,994,944 | 155,675,787 |
| **单层合计** | **209,723,904** | **216,225,345** |
| **8 层合计** | **1,677,791,232** | **1,729,802,760** |

单层 216.23 MB，8 层 1.73 GB，占每 token 8.183 GB 的 21.1%。每个字节只贡献约 0.97 MAC，和 A 层一样是纯带宽题。

### 6.2.4 引擎准入：哪些算子不能上

| 算子 | MAC | 能否上矩阵引擎 | 原因 |
|---|---:|---|---|
| q/k/v/o 投影、MLP | 209,727,488 | 能 | K 维规整，N 是 128 的倍数 |
| QKᵀ、A·V | 8,192·S | 能（M=16 的 GEMV） | 需要 KV 广播，效率低但可上 |
| q_norm / k_norm | 10,240 | 不能 | 256 宽行内归约 |
| 部分 RoPE | 2,560 | 不能 | 跨 32 元素配对的置换 |
| softmax | 80·S | 不能 | 逐行归约 + 超越函数 |
| 输出门控 | 8,192 | 不能 | 逐元素 sigmoid |
| KV 追加 | 0 | 不能 | 纯搬运 |

能上的部分占 MAC 的 99.98%，占权重的 100%。不能上的部分是纯延迟项，在 decode 的 26.4 ms 里占不到 0.5 ms，但它们决定流水线能不能填满。

### 6.2.5 七个必须记住的坑（按危害排序）

1. **q_proj 逐头切分**（B②）。按 4096 全局二分是错的，且不报错。
2. **q_norm 按 head_dim 256**（B④）。按 4096 归一会把 16 个头混在一起，输出发散。
3. **RoPE 只转 64 维、half-split**（B⑤）。interleaved 实现不会报错，只会答非所问。
4. **K 在写入 cache 前必须已旋转，V 不旋转**（B⑥）。顺序反了，短上下文正常、长上下文崩。
5. **GQA 4:1 用广播**（B⑦）。16 个 q 头各读一份 KV，带宽变 4 倍。
6. **门控在 o_proj 之前**（B⑩）。放到之后，等价于给输出乘两次门控。
7. **KV 分页的页大小**（B⑥）。4 KB/token/层，页大小若不是 4 KB 的整数倍，长上下文会有 30% 以上的写放大。

### 6.2.6 与其它章节的接口

- 结构图与 DAG 在 3.4；本节的卡只是把每个 G 组拆细。KV 的三档数字进入 8.3 的缓存账与 9.1 的 decode 时间线。
- 门控与 q_proj 的融合方式影响 10.1 的引擎分类；MTP 层（6.4）复用本节全部 12 张卡，只是权重换成了 `mtp.layers.0.*`。

## 6.3 子模块 C：SwiGLU MLP（32 层共用）

### M1 前置 RMSNorm

- 形状与 MAC：`[1,4096] → [1,4096]`；8,192；归属 VPU。
- 精度与坑：FP32 平方和；32 层各一次，与 A/B 层的入口 norm 是同一单元。

### M2 gate/up 双投影

- 形状与 MAC：`[1,4096] → [1,12288]` ×2；50,331,648 ×2 = 100,663,296；归属矩阵引擎，N=12288。
- 精度与坑：MXFP8，FP32 累加；两路共用同一份 r（4096×2 B = 8 KB），激活只读一次、复用两次；若分两次 kernel，激活带宽翻倍。

### M3 SwiGLU 门控

- 形状与 MAC：`[1,12288]`；12,288；归属 VPU（含 12,288 次 SiLU）。
- 精度与坑：FP16，LUT 32 KB；`silu(g) ⊙ u` 的乘法必须在 SiLU 之后；12288 维一次做完，不要按 4096 分三块。

### M4 down 投影

- 形状与 MAC：`[1,12288] → [1,4096]`；50,331,648；归属矩阵引擎，K=12288。
- 精度与坑：MXFP8，FP32 累加（96 级折叠流水）；K 是层内最长，累加器 16 KB；tile 的 K 方向要能装下 12288 或分块累加不溢出。

### M5 残差加

- 形状与 MAC：`[1,4096]`；4,096；归属 VPU。
- 精度与坑：同 A⑭；层内第二次写回主总线。

### M6 MLP 合计与硬件含义

| 项 | 数值 |
|---|---:|
| 单层 MAC | 151,007,232 |
| 单层参数 | 150,994,944 |
| 单层权重（MXFP8） | 155,675,787 B |
| 32 层 MAC | 4,832,231,424 |
| 32 层权重 | 4,981,625,184 B |
| 占每 token MAC | 60.6% |
| 算术强度 | 0.97 MAC/B |

MLP 是全模型最大的一块算力（4.83 GMAC，60.6%），也是最大的一块权重（4.98 GB，60.9%）。32 层结构完全同构，是最适合先上矩阵引擎、先做 MXFP8、先做 tiling 优化的部分。三个矩阵都是 4096↔12288，M=1 时算术强度 0.97 MAC/B，是一道纯粹的带宽题。

引擎吞吐下限：要在 310 GB/s 下不被算力拖住，引擎需要 `151,007,232 ÷ (155,675,787 ÷ 310e9) = 300.7 GMAC/s`（≈ 601 GFLOPS）。这个数字对全模型通用——每 1 B/参数的带宽配约 1 GMAC/s 的引擎吞吐，310 GB/s 的 DRAM 配 300 GMAC/s 的引擎刚好平衡。引擎再快也没有用，除非上投机解码把 M 拉大。

## 6.4 层型 D：MTP（1 层）

### D1 `pre_fc_norm_hidden`

- 形状与 MAC：`[1,4096] → [1,4096]`；8,192；归属 VPU。
- 精度与坑：FP32 平方和；输入是上一层的 hidden，不是本层输出。

### D2 `pre_fc_norm_embedding` + 查表

- 形状与 MAC：embedding 行 `[1,4096]`，归一化后 `[1,4096]`；8,192（查表 0）；归属 VPU + DMA。
- 精度与坑：embedding 权重 BF16；查表读 8 KB，与主干的 `embed_tokens` 是同一张表；`mtp_use_dedicated_embeddings=false`，不要单独复制一份。

### D3 concat + `mtp.fc`

- 形状与 MAC：`[1,8192] → [1,4096]`；33,554,432；归属矩阵引擎。
- 精度与坑：MXFP8；两路 4096 的拼接顺序是 [hidden | embedding]，反了模型仍能跑但草稿全错。

### D4 `mtp.layers.0`

- 形状与 MAC：同层型 B，含自己的 KV cache（4 头）；209,773,056 + 8,272·S；归属同 B。
- 精度与坑：同 B；它是全注意力而不是 DeltaNet；KV 每 token 4,096 B，256K 时额外 1.07 GB。

### D5 `mtp.norm` + 共享 `lm_head`

- 形状与 MAC：`[1,4096] → [1,4096] → logits [248320]`；8,192 + 1,017,118,720；归属 VPU + 矩阵引擎。
- 精度与坑：norm FP32 归约；lm_head MXFP8；草稿 token 也要过完整的 1.049 GB lm_head，这是投机解码成本的 80%。

### D6 投机解码的数学

接受率 α、草稿长度 γ 时，每步期望产出：

```
E[emitted] = (1 − α^(γ+1)) / (1 − α)
```

每步要读的权重 = γ × 草稿权重 + 1 × 验证权重（全模型）。草稿权重含 lm_head 时 1.299 GB（250.83 MB MTP + 1.049 GB lm_head）：

| 方案 | γ | α | E[emitted] | 每步字节 | 加速比 |
|---|---:|---:|---:|---:|---:|
| 共享 248K 词表 | 2 | 0.7 | 2.19 | 10.781 GB | 1.66× |
| 共享 248K 词表 | 2 | 0.8 | 2.44 | 10.781 GB | 1.85× |
| 8K 草稿头 | 3 | 0.7 | 2.53 | 9.039 GB | 2.29× |
| 8K 草稿头 | 3 | 0.8 | 2.95 | 9.039 GB | 2.67× |

8K 草稿头把 lm_head 从 1.049 GB 压到 34.59 MB（8192×4096 参数），草稿权重降到 285.43 MB。加速比的分母是每步字节/基线字节，分子是 E[emitted]，两者都随 γ 增长，但字节增长得更快，所以 γ=3 的收益主要来自换词表，不是加长度。

### D7 拒绝时的回滚要求

- KV cache 回滚简单：8 层 × 4,096 B/token，丢弃被拒 token 的页即可。
- DeltaNet 状态不能回滚：`S ← S·diag(e^g) + β·kᵀ(v − S·k)` 是破坏性写，没有逆运算。要么每个草稿 token 前存一份快照（50,331,648 B × 3 = 151 MB/步，占验证流量的 1.7%），要么接受后重算被拒前缀（一次 γ+1 token 的前向，等于再做一次验证）。conv_state 同理，1,179,648 B/份，相对可以忽略。
- 结论：9B 上投机解码的隐藏成本是 **151 MB/步的状态快照**，这是 0.8B（56.6 MB/3 份）的 2.67 倍，和状态本身同比例。

### D8 MTP 总账

| 项 | 数值 |
|---|---:|
| 参数 | 243,290,624 |
| 草稿权重（MXFP8，含 lm_head） | 1,299,482,033 B |
| 草稿 MAC/token | 1,260,470,784 |
| 占全模型参数 | 2.5% |
| 占每 token 权重 | 15.9% |

MTP 头本身只占 2.5% 的参数，但每个草稿 token 要读 15.9% 的权重。它把"M=1 的带宽题"变成"M=γ+1 的算力题"，前提是词表足够小，否则 lm_head 会把收益吃掉。

---
---

# 第 7 章　视觉塔与输出头

## 7.1 视觉塔结构

27 个 ViT block，hidden 1152、16 个头、head_dim 72、intermediate 4304。家族里第一次出现 head_dim 不是 64 的视觉塔（0.8B/2B 是 64），72 = 1152/16，不是 2 的幂。

```
图 448×448×3
  │
  ├─ [V①] patch_embed  conv3d(3→1152, kernel 2×16×16, stride 2×16×16)  ─► 784 × 1152
  ├─ [V②] + pos_embed（2304 个位置的 bicubic 插值）                     ─► 784 × 1152
  │
  ├─ 27 × block:
  │    ├─ [V③] LayerNorm → [V④] qkv → [V⑤] QKᵀ → [V⑥] softmax → [V⑦] A·V
  │    │        └ 2D axial RoPE（72 维全转，spatial 36 + temporal 36）
  │    ├─ [V⑧] proj → 残差
  │    └─ [V③] LayerNorm → [V⑨] fc1(4304) → GELU-tanh → fc2 → 残差
  │
  └─ [V⑩] merger: 2×2 邻域拼成 4608 → fc1(4608) → GELU-erf → fc2(4096)  ─► 196 × 4096
```

一张 448² 的静图按 `temporal_patch_size=2` 复制成 2 帧，patch 数 28×28 = 784；`spatial_merge_size=2` 把 2×2 邻域合并，输出 196 个视觉 token。196 个 token 进文本主干，每 token 4096 维，与文本 embedding 同宽。

## 7.2 视觉塔逐算子拆解（V①–V⑩）

| 卡 | 算子 | 形状（in → out） | MAC/图 | 占比 |
|---|---|---|---:|---:|
| V① | patch_embed | `[784,1536] × [1536,1152]` | 1,387,266,048 | 0.37% |
| V② | pos_embed 插值 | `[784,1152]`，4 邻域双三次 | 3,612,672 | 0.00% |
| V③ | 2×LayerNorm + 2×残差 + 2D RoPE（×27） | `[784,1152]` | 341,397,504 | 0.09% |
| V④ | qkv（×27） | `[784,1152] × [1152,3456]` | 84,276,412,416 | 22.68% |
| V⑤ | Q·Kᵀ（×27） | `[16,784,72] × [16,72,784]` | 19,118,260,224 | 5.15% |
| V⑥ | softmax（×27） | `[16,784,784]` | 1,327,656,960 | 0.36% |
| V⑦ | A·V（×27） | `[16,784,784] × [16,784,72]` | 19,118,260,224 | 5.15% |
| V⑧ | proj（×27） | `[784,1152] × [1152,1152]` | 28,092,137,472 | 7.56% |
| V⑨ | fc1 + GELU-tanh + fc2（×27） | `[784,1152] ↔ [784,4304]` | 210,001,800,960 | 56.52% |
| V⑩ | merger | `[196,4608] × [4608,4608]` + `[196,4608] × [4608,4096]` | 7,863,883,776 | 2.12% |
| | **合计** | | **371,530,688,256** | **100.00%** |

按类别合并：

| 类别 | MAC | 占比 |
|---|---:|---:|
| 线性 GEMM（qkv、proj、MLP） | 322,370,350,848 | 86.77% |
| 注意力（QKᵀ + A·V） | 38,236,520,448 | 10.29% |
| merger | 7,863,883,776 | 2.12% |
| softmax | 1,327,656,960 | 0.36% |
| patch + pos | 1,390,878,720 | 0.37% |
| 归一化 + RoPE + 残差 | 341,397,504 | 0.09% |
| **合计** | **371,530,688,256** | **100.00%** |

三件事值得单独说。

**MLP 占了视觉塔的 56.5%。** 4304 = 16 × 269，269 是质数，所以 N 方向没有好的 tile 切分：按 128 切是 33.6 块，最后一块只有 80 列（利用率 62.5%）；按 64 切是 67.25 块，最后一块 16 列。整塔因此损失 1.1% 的引擎利用率。

**head_dim 72 是硬伤。** QKᵀ 与 A·V 的 K 维是 72，不是 64 的倍数。按 64 切要补 56 列（87.5% padding），按 8 切正好 9 块但累加链变短。A·V 的 N=72 必须补到 128，阵列有 44% 的周期在算 padding；这是 38.24 G 注意力 MAC 里被浪费的 16.7 G。

**merger 的 M 是 196，不是 784。** 按 784 算是错的（会高估 4 倍）：`x.view(-1, 4608)` 已经把 2×2 邻域拼成一行。真实值是 7.86 G，不是 31.5 G。

## 7.3 视觉塔的成本

| 项 | 数值 |
|---|---:|
| 参数 | 456,010,480 |
| 权重（MXFP8） | 470,146,805 B |
| MAC/图（448²） | 371,530,688,256 |
| 算术强度 | 790 MAC/B |
| 算力时间 @25 TOPS | 29.72 ms |
| 读权重时间 @310 GB/s | 1.52 ms |
| 算力/带宽比 | 19.6 : 1 |

视觉塔是**算力受限**的，而且受限得很彻底：算力时间是读权重时间的 19.6 倍。同一个引擎在文本 decode 上是 1 MAC/B 的带宽题，在这里是 790 MAC/B 的算力题，相差 800 倍。

| 图尺寸 | patch 数 | 视觉 token | MAC | 相对 448² |
|---|---:|---:|---:|---:|
| 448 × 448 | 784 | 196 | 371.53 G | 1.00× |
| 1024 × 1024 | 4,096 | 1,024 | 2,814.25 G | 7.57× |

1024² 的 7.57 倍里，线性部分只涨 5.22 倍（token 数），attention 涨 27.3 倍（token 数平方）。448² 时 attention 只占 10.3%，1024² 时占到 37.1%——`N²` 项把视觉塔从"MLP 主导"推向"attention 主导"。

折合文本：196 个视觉 token 的算力是 371.53 G，每个 1.896 G，只等于 0.27 个文本 prefill token（6.959 G）。连同这 196 个 token 走文本主干，一张 448² 图的总计算量是 **1,735.49 GMAC**，约等于 249 个文本 prefill token。

## 7.4 视觉塔的矩阵引擎映射

| 算子 | M | N | K | tile 建议 | 问题 |
|---|---:|---:|---:|---|---|
| patch_embed | 784 | 1152 | 1536 | 128×128×64 | K 不是权重矩阵形式，是 conv3d 展开 |
| qkv | 784 | 3456 | 1152 | 128×128×64 | N=3456=27×128，正好 |
| QKᵀ | 16×784 | 784 | 72 | 64×64×8 | K=72 需 9 个 8 宽块 |
| A·V | 16×784 | 72 | 784 | 64×64×64 | N=72 需补到 128，利用率 56% |
| proj | 784 | 1152 | 1152 | 128×128×64 | 无 |
| fc1 | 784 | 4304 | 1152 | 128×128×64 | N=4304=33.6×128 |
| fc2 | 784 | 1152 | 4304 | 128×128×64 | K=4304=67.25×64 |
| merger fc1 | 196 | 4608 | 4608 | 128×128×64 | M=196 只有 2 个满 tile |
| merger fc2 | 196 | 4096 | 4608 | 128×128×64 | 同上 |

两个结论。第一，除了 attention，所有 GEMM 的 M 都是 784 或 196，足够大，能跑满阵列——这是视觉塔算术强度 790 MAC/B 的来源。第二，head_dim 72 让 attention 的 N 方向补到 128，A·V 的实际利用率只有 56%，这是 38.24 G MAC 里被浪费的 17 G。

## 7.5 视觉塔的坑（速查）

1. **2D axial RoPE 转满 72 维**，不是部分旋转。空间 36 维（18 个频率/轴）、时间 36 维；按 64 维实现会错位。
2. **GELU 有两种**：block 内是 `gelu_pytorch_tanh`，merger 里是 `gelu`（erf）。混用不会报错，精度掉 0.1–0.3%。
3. **`use_postshuffle_norm` 分支**：merger 是先 norm 再 shuffle，还是先 shuffle 再 norm，取决于这个开关；两种顺序数值不同，移植前必须查 config 与源码，不能照抄别的家族成员。
4. **静图复制成 2 帧**：`temporal_patch_size=2`，patch_embed 的 K 是 3×2×16×16 = 1536，不是 768；merger 的 M 是 196，按 784 算高估 4 倍。
5. **`deepstack_visual_indexes` 为空**，没有多层特征回流；若按有 deepstack 的家族成员实现，会多算 3 次 4096 维投影。
6. **pos_embed 是 2304 个位置（48×48）**，448² 只有 28×28，必须双三次插值，不能直接切。block 的 qkv 是 3456 = 3×1152 且带 bias，四个 bias 加两个 LN 共 14,672 个参数，不能省。

## 7.6 输出头与采样

`lm_head` 是 [248320, 4096]，1,017,118,720 MAC 与 1,048,649,400 B。decode 每 token 都要过一遍，占 12.8% MAC 与 12.8% 带宽。

采样本身不是矩阵运算，但在 248320 的词表上是实打实的延迟：

| 步骤 | 操作数 | 说明 |
|---|---:|---|
| 取 max | 248,320 | 找 logit 最大值 |
| top-k / top-p 筛选 | 248,320 × 4 | 排序或阈值扫描 |
| exp + 归一 | 248,320 × 2 | softmax |
| 累积分布 + 采样 | 248,320 × 2 | 逆变换采样 |
| **合计** | **约 2.2 M 次操作** | 约 4.5 M 次比较（含排序） |

在 1 GHz、每拍 1 次比较的 VPU 上，4.5 M 次比较是 4.5 ms；若排序用二分插入则是 5–10 ms。对照 decode 的 26.4 ms 总时长，采样占 **19–38%**——这是最容易被忽略的一块。硬件上应把 top-k 做成 248320 路的并行比较树（约 18 级），把 4.5 ms 压到 0.3 ms。

贪心解码可以省掉排序，只做一次 argmax（248,320 次比较，0.25 ms）。若产品只做贪心，输出头的延迟可以忽略；只要开 top-p，就必须为它留一条独立的向量通路。

---
---
# 第四部分　核算与映射

---

# 第 8 章　三本账：算力 / 带宽 / 缓存

每 token 约 7.976 G MAC，其中 MLP 占 60.6%、lm_head 12.8%、DeltaNet 层（含各自的 MLP）66.2%。每 token 要读 8.183 GB 权重（MXFP8），这是 decode 速度的决定因素。缓存从"一小一大"变成"两大"：DeltaNet 状态 50.33 MB 不再是小项，KV cache 256K 时 8.59 GB。

## 8.1 算力账（decode，S 较短）

| 组件 | 数量 | 单个 MAC | 小计 | 占比 |
|---|---:|---:|---:|---:|
| 层型 A（DeltaNet，含 MLP） | 24 | 220,033,152 | 5,280,795,648 | 66.2% |
| 层型 B（Attention，含 MLP，不含 S 项） | 8 | 209,773,056 | 1,678,184,448 | 21.0% |
| Attention 的 QKᵀ + A·V + softmax | 8 | 8,272·S | 66,176·S | 随 S 涨 |
| lm_head | 1 | 1,017,118,720 | 1,017,118,720 | 12.8% |
| embedding | 1 | 0 | 0 | 0.0% |
| **合计（S 较短）** | | | **7,976,098,816** | **100.0%** |
| **S = 4096 时** | | | **8,247,155,712** | |
| **S = 32768 时** | | | **10,144,553,984** | |
| **S = 262144 时** | | | **25,323,740,160** | |

7.976 G MAC 折成浮点口径是 15.95 GFLOP/token。

MLP 是最大的计算块，32 层合计 4.832 G，占 60.6%。它的形状最规整，优化它最省力。

lm_head 是第二大的单个算子，1.017 G，占 12.8%。它只随 hidden 线性缩小，而层内参数随 hidden 平方缩小，所以模型越小、lm_head 的占比越高：0.8B 33.1%、2B 26.8%、9B 12.8%。这是家族里第一次 lm_head 不再是首位成本，也是"9B 可以靠量化 lm_head 省容量"这个判断成立的原因。

DeltaNet 占了 75% 的层数、66.2% 的算力，每层 220.03 M 比 attention 层的 209.77 M 大 4.9%。差别全在 mixer：A 的四个投影 50.33 M 对 B 的 q/k/v/o 58.72 M，但 A 多了 delta 递推的 1.57 M 与卷积的 0.03 M，净差 10.26 M。两者参数只差 4.1%，算力差 4.9%。

## 8.2 带宽账（decode，MXFP8 = 8.25 bit = 1.031 B/参数）

| 项 | 参数量 | 字节 |
|---|---:|---:|
| 层型 A × 24 | 5,241,770,496 | 5,404,265,381 |
| 层型 B × 8 | 1,677,791,232 | 1,729,802,760 |
| lm_head | 1,017,118,720 | 1,048,649,400 |
| final norm | 4,096 | 4,223 |
| **合计** | **7,936,684,544** | **8,182,721,764** |

合计行是逐行取整后相加；精确乘积 7,936,684,544 × 1.031 = 8,182,721,765，差 1 B。

| 附加流量（S=4096） | 大小 | 说明 |
|---|---:|---|
| KV cache 读 | 134,217,728 B | 8 层 × 4 头 × 256 × 4096 × 2 × 2 B |
| DeltaNet 状态读写 | 100,663,296 B | 50.33 MB × 2，常驻则只在 SRAM |
| conv 状态读写 | 2,359,296 B | 1.18 MB × 2 |
| 激活 | < 2 MB | 残差流 + 层内临时量，可忽略 |

**速度上限**（310 GB/s）：

```
只看权重：8.183 GB ÷ 310 GB/s = 26.40 ms → 37.9 token/s
S=4096：  8.418 GB ÷ 310 GB/s = 27.15 ms → 36.8 token/s
实际效率 60~70%                → 22~26 token/s
```

对照 0.8B 的 0.776 GB / 400 tok/s、2B 的 1.941 GB / 160 tok/s：9B 权重大 10.5 倍，速度慢 10.5 倍，没有额外损失。三档的算术强度分别是 0.99 / 0.98 / 0.97 MAC/B，几乎相同——hidden 翻倍时权重与激活同步翻倍，比值不变。

| 项 | 0.8B | 2B | 9B |
|---|---:|---:|---:|
| 每 token 权重 | 0.776 GB | 1.941 GB | 8.183 GB |
| 上限 @310 GB/s | 400 tok/s | 160 tok/s | 37.9 tok/s |
| 实际 60–70% | 225–263 | 96–112 | 22–26 |
| MAC/token | 0.767 G | 1.896 G | 7.976 G |
| 算术强度 | 0.99 MAC/B | 0.98 MAC/B | 0.97 MAC/B |
| 引擎吞吐下限 | 307 GMAC/s | 302 GMAC/s | 302 GMAC/s |

同一个引擎规格能同时服务三档，因为瓶颈比不变。反过来说，"把模型做小"只能按比例提速，不能改变 roofline 上的位置。

## 8.3 缓存账

| 项 | 公式 | S=4K | S=32K | S=256K |
|---|---|---:|---:|---:|
| DeltaNet 状态（FP32） | 24 × 32 × 128 × 128 × 4 B | 50.33 MB | 50.33 MB | 50.33 MB |
| DeltaNet conv 状态（FP16） | 24 × 8192 × 3 × 2 B | 1.18 MB | 1.18 MB | 1.18 MB |
| Attention KV（FP16） | 32,768·S B | 134.22 MB | 1.07 GB | **8.59 GB** |
| **合计** | | **185.73 MB** | **1.13 GB** | **8.64 GB** |

三档模型的对照：

| 项 | 0.8B | 2B | 9B |
|---|---:|---:|---:|
| 总层数 / DeltaNet 层 | 24 / 18 | 24 / 18 | 32 / 24 |
| value 头数 | 16 | 16 | 32 |
| 单头状态 | 128 × 128 | 128 × 128 | 128 × 128 |
| 状态合计（FP32） | 18.87 MB | 18.87 MB | 50.33 MB |
| conv 状态 | 0.66 MB | 0.66 MB | 1.18 MB |
| KV 头 / 层 | 2 / 6 | 2 / 6 | 4 / 8 |
| KV 每 token | 12,288 B | 12,288 B | 32,768 B |
| 256K KV | 3.22 GB | 3.22 GB | 8.59 GB |
| 权重/KV 拐点 | 63K | 158K | 250K |

第一件事变了。18.9 MB 曾经是家族里"与模型大小无关的常数"，9B 上变成 50.33 MB：单头 128×128 和 4-tap 卷积没变，但 value 头翻倍、层数多 8。2.67 倍全部来自并行度，不是来自几何。落 DRAM 的话每 token 多 100.7 MB 流量，占总带宽的 1.2%；代价不是带宽而是延迟——3 个 pass 变成 3 次 DRAM 往返，每次约 500 ns，24 层就是 36 µs，仍在 26.4 ms 的 0.14% 以内。

第二件事也变了。256K 上下文要 8.59 GB 的 KV，比权重 8.183 GB 还大。重量与 KV 的拐点在 250K 上下文（8.183 GB ÷ 32,768 B），是家族里唯一"权重几乎永远主导"的一档：0.8B 拐在 63K、2B 拐在 158K，只有 9B 的拐点接近最大上下文。

第三件事是容量。全模型 9.653 B 参数按 MXFP8 是 **9.952 GB**，8 GB 设备装不下（差 1.95 GB），12 GB 装得下但只剩约 61K token 的 KV，16 GB 配 FP8 KV 才能覆盖 256K（14.30 GB）。这是 9B 与另外两档最本质的产品差异：不是算得慢，是装不下。

---

---

# 第 9 章　时间线：一个 token 走完全程

把前面算出的 MAC 和字节数按执行顺序排一遍，看时间到底花在哪。三种负载分开算：decode 一个 token、prefill 一段 prompt、一张图的前向。

## 9.1 Decode 一个 token（S=4096）

```
 1  查表 embed[token_id]                    8 KB DRAM 读            VPU
 2  for layer 0..31:
 3      RMSNorm                             8 K MAC                 VPU
 4      ┌ 若为 DeltaNet（24 层）：
 5      │   in_proj_qkv + z + a + b          50.59 M MAC             引擎    ← 读 52.1 MB
 6      │   conv1d + SiLU                    32.8 K MAC + 8192 exp   VPU     ← 读写 96 KB 状态
 7      │   L2 norm(q,k)                     8 K MAC                 VPU
 8      │   β、g                             32 维超越函数            VPU     ← FP32 关键路径
 9      │   ── delta 递推 ──                 1.57 M MAC              递推    ← 3 拍串行，读写 4.2 MB 状态
10      │   RMSNormGated                     16 K MAC                VPU
11      │   out_proj                         16.78 M MAC             引擎    ← 读 17.3 MB
12      └─ 若为 Attention（8 层）：
13      │   q_proj + k_proj + v_proj        41.94 M MAC             引擎    ← 读 43.2 MB
14      │   q/k norm + RoPE                 12.8 K MAC               VPU
15      │   写 K/V cache                     4 KB 写                 DMA
16      │   QKᵀ + softmax + A·V             33.88 M MAC            引擎+VPU ← 读 134 MB KV
17      │   门控 + o_proj                    16.79 M MAC             引擎    ← 读 17.3 MB
18      ├─ 残差 + post RMSNorm              12.3 K MAC              VPU
19      └─ MLP: gate/up → silu → down       151.01 M MAC            引擎    ← 读 155.7 MB
20  final norm + lm_head                    1.017 G MAC             引擎    ← 读 1.049 GB
21  采样（top-p）                            4.5 M 次比较            VPU
```

按字节归并：

| 阶段 | 字节 | 时间 @310 GB/s | 占比 |
|---|---:|---:|---:|
| 24 × DeltaNet 层 | 5,404,265,381 | 17.43 ms | 66.04% |
| 8 × Attention 层 | 1,729,802,760 | 5.58 ms | 21.15% |
| final norm + lm_head | 1,048,653,623 | 3.38 ms | 12.81% |
| **合计（仅权重）** | **8,182,721,764** | **26.40 ms** | **100.00%** |

表外还有附加流量：KV + 状态（S=4096）237,240,320 B，折 0.77 ms，不计入上面的合计。

第一，17.43 ms 的 DeltaNet 里有 1.57 M MAC 是递推，按 256 MAC/拍算是 6,144 拍/层、24 层 147 µs，只占 0.56%，但它串在关键路径上，无法用"多读几个权重"来掩盖。

第二，5.58 ms 的 Attention 里有 0.43 ms 是读 KV。S=262144 时这一项涨到 27.7 ms（8.59 GB），超过全部权重——attention 层的时间会从 6 ms 变成 33 ms。

第三，lm_head 的 3.38 ms 与采样 4.5 ms 是串行的，两者合计 7.9 ms，占 26.4 ms 的 30%。输出头的成本第一次接近 attention 层。

## 9.2 Prefill 一段 prompt（S=1024）

| 项 | MAC | 说明 |
|---|---:|---|
| 线性项（32 层 + 投影 + MLP） | 7,125,995,618,304 | 6.959 G × 1024 |
| 因果注意力（8 层，`S(S+1)/2`） | 34,729,164,800 | 8 × 8,272 × 524,800 |
| lm_head（末 token） | 1,017,118,720 | 只算一次 |
| **合计** | **7,161,741,901,824** | 14.32 TFLOP |

25 TOPS（12.5 TMAC/s）下 **573 ms**，每 prompt token 0.560 ms。权重只读一遍 8.183 GB，26.4 ms；算力时间是带宽时间的 21.7 倍，prefill 是彻底的算力受限。

decode 一个 token 是 26.40 ms，是 prefill 单 token 的 **47.2 倍**。也就是说，一段 1024 token 的 prompt，其 prefill 时间等于解码 47.2 个 token。对交互式产品，TTFT 与 TPOT 的比例由这个 47.2 决定：prompt 越长，用户越是在等"47 份 decode 的钱一次性付清"。

## 9.3 一张图的前向（448×448）

| 阶段 | MAC | 时间 @25 TOPS |
|---|---:|---:|
| 视觉塔（V①–V⑩） | 371,530,688,256 | 29.72 ms |
| 196 个视觉 token 过文本主干（prefill） | 1,363,960,098,816 | 109.12 ms |

196 × 6.959 G = 1,363.96 G，按 12.5 TMAC/s 是 109.12 ms。加视觉塔共 138.84 ms。若这 196 个 token 走 decode（一次一个），则是 196 × 26.40 ms = 5.17 s——差 37 倍，这是"图像必须走 prefill 路径"的量化理由。

视觉塔的 29.72 ms 里，MLP 16.8 ms（56.5%）、attention 3.1 ms（10.3%）、qkv+proj 9.0 ms、merger 0.63 ms。它是一段 196 token、hidden 1152 的 prefill，算术强度 790 MAC/B，引擎在这个形态下几乎不浪费。

---
---

# 第 10 章　算子 → 硬件映射

## 10.1 分类矩阵

| 类别 | 本模型里的算子 | 量级（每 token） | 归属 |
|---|---|---|---|
| 稠密 GEMM / GEMV | 所有投影、lm_head、QKᵀ、A·V、视觉塔 GEMM | 7.97 G MAC | MXFP8 矩阵引擎 |
| 归约 | RMSNorm ×65、q/k norm ×16、softmax ×8、L2 ×48 | 约 1.2 M MAC | VPU（4096/256/128 宽） |
| 逐元素 | SiLU、sigmoid、门控乘、残差、RoPE | 约 0.9 M MAC | VPU（12288 宽） |
| 串行递推 | delta 状态更新 | 1.57 M MAC | 递推单元（FP32） |
| 搬运 | KV 写、`repeat_interleave`、量化 pass | 134 MB + 384 KB 读放大 | DMA |

矩阵引擎承担 99.5% 的 MAC，但只承担 0% 的延迟关键路径。递推单元与 VPU 承担剩下的 0.5%，却决定了 24 层递推的 147 µs、105 次归约的 1,260 拍和 8 层 softmax 的串行链。

## 10.2 引擎侧的三条结论

**吞吐下限 302 GMAC/s。** 7.976 G MAC ÷ 8.183 GB × 310 GB/s = 302 GMAC/s。这是 M=1 的 MXFP8 GEMV 在 310 GB/s 下不被算力拖住的最低规格，三档模型都一样。引擎做到 300 GMAC/s 就够了，做到 1 TMAC/s 只对 prefill 与投机解码有意义。

**SRAM 下限 50.33 MB。** 24 层 DeltaNet 状态若全驻片上，需要 50.33 MB（FP32）+ 1.18 MB conv + 单层 KV 页。这是 9B 相对 0.8B 最硬的约束：18.87 MB 还能塞进 32 MB SRAM，50.33 MB 塞不进 64 MB 以下任何一档。落 DRAM 的代价是每 token 100.7 MB 流量与 36 µs 的额外延迟，可以接受；真正的风险是 3 个 pass 的依赖让这 100.7 MB 无法预取。

**长上下文的重心转移到 KV。** S=262144 时 KV 读 8.59 GB/token，是权重的 1.05 倍；attention 的 QKᵀ+A·V 是 17.18 G MAC，是固定部分的 2.15 倍。引擎在长上下文下的形态从"权重流 GEMV"变成"KV 流 GEMV"，两者的算术强度都接近 1 MAC/B，但后者的地址生成复杂度高一个数量级（分页、4:1 广播、因果 mask）。

## 10.3 MTP 与投机解码：能绕开内存墙，但要先算草稿成本

MTP 把 decode 的 M 从 1 变成 γ+1，理论上能把带宽题变成算力题。9B 上有两个额外约束。

草稿不便宜。MTP 头 243.29 M 参数，但草稿 token 要过共享的 lm_head（1.049 GB），草稿权重 1.299 GB，是基线 8.183 GB 的 15.9%。

状态快照不便宜。DeltaNet 状态不可逆，γ 个草稿 token 要 γ 份 50.33 MB 的快照（γ=3 时 151 MB），否则被拒后只能重算整个前缀。

| 方案 | 加速比 | 验证 pass 的算术强度 | 引擎需求 |
|---|---:|---:|---:|
| γ=2，共享 248K 词表，α=0.7 | 1.66× | 2.22 MAC/B | 688 GMAC/s |
| γ=3，8K 草稿头，α=0.7 | 2.29× | 3.53 MAC/B | 1.09 TMAC/s |
| γ=3，8K 草稿头，α=0.8 | 2.67× | 3.53 MAC/B | 1.09 TMAC/s |

第三列是验证 pass 的算力/带宽比：γ=3 时一次验证 4 个 token、31.9 GMAC、9.039 GB。要让验证 pass 不退化回带宽受限，引擎至少 1.09 TMAC/s，是 302 GMAC/s 下限的 3.6 倍。这就是"要不要为投机解码把引擎做大 3.6 倍"的决策点：收益 2.3 倍、代价 3.6 倍算力，只有在 prefill 也吃满引擎时才划算。

对 9B 的判断：投机解码的主要收益来自把 prefill 与 decode 拉平，而不是单纯加速 decode。在 8 GB/12 GB 设备上，1.299 GB 的草稿权重会挤掉 40K token 的 KV，得不偿失；在 16 GB 以上、且引擎已按 1 TMAC/s 规划时，γ=3 + 8K 草稿头是唯一能把 decode 拉到 2 倍以上的组合。

---
---

# 第 11 章　量化归属

## 11.1 逐项归属

| 块 | 参数 | 占比 | 建议精度 | 体积 |
|---|---:|---:|---|---:|
| 24 × DeltaNet 层 | 5,241,770,496 | 54.30% | MXFP8 | 5.404 GB |
| 8 × Attention 层 | 1,677,791,232 | 17.38% | MXFP8 | 1.730 GB |
| `lm_head` | 1,017,118,720 | 10.54% | MXFP8 | 1.049 GB |
| `embed_tokens` | 1,017,118,720 | 10.54% | MXFP8 或 BF16 | 1.049 GB |
| 视觉塔 | 456,010,480 | 4.72% | MXFP8（patch_embed 留 FP16） | 0.470 GB |
| MTP 头 | 243,290,624 | 2.52% | MXFP8 | 0.251 GB |
| norm / `A_log` / `dt_bias` | 4,096 + 3,840 | 0.00% | FP32 | < 1 MB |
| **合计** | **9,653,104,368** | **100.00%** | | **9.952 GB** |

体积列按 1.031 B/参数算，只有全模型装在一起时才需要相加；纯文本推理只读前两行加 lm_head，8.183 GB。

## 11.2 三条硬规则

**规则一：状态与门控参数不量化。** `recurrent_state` 50.33 MB 必须 FP32：它是跨 token 累加的量，FP16 在 32 层、数千 token 后会漂移到不可用。`A_log`、`dt_bias`、`linear_attn.norm` 合计 3,840 个元素，量化收益 7,680 B（0.00004%），风险是整个递推发散。这三块是量化版图上的飞地，任何"全模型 INT8/FP8"的工具链都必须为它们留例外。

**规则二：逐元素与门控留 FP16。** SiLU 的负半轴把相对误差放大（`silu(−8) ≈ −2.7e-3`），E4M3 在负半轴相对误差可达 6%；sigmoid 的输出要乘到 4096 维残差上，1% 的门控误差就是 1% 的整层输出误差；RoPE 的角度必须 FP32，旋转本身可以 FP16。这三类算子占 MAC 不到 0.05%，量化它们省不下带宽，只会引入误差。

**规则三：词表单独决策。** `embed_tokens` 与 `lm_head` 各 1,017,118,720，合计 2.034 B，占容量 21.1%，但只占每 token 带宽的 12.8%（embed 只读一行 8 KB）。这意味着：量化词表主要省**容量**，不省**速度**。若产品受容量约束，先压词表；若受带宽约束，压词表几乎没有用。

## 11.3 lm_head 的特殊处理

`lm_head` 是 [248320, 4096]，1.017 GMAC、1.049 GB，占每 token 的 12.8% MAC 与 12.8% 带宽。它有三个特殊之处。

第一，它不共享权重。`tie_word_embeddings=false` 意味着输入 embedding 与输出投影是两份独立矩阵，容量 2.034 B。想省下 1.049 GB，只能重新训练一个 tied 版本或做词表蒸馏，不能靠运行时技巧。

第二，logits 不能只算一部分。top-p 采样需要全部 248320 个值，不能像检索那样只算候选集；但可以把 4096×248320 的 GEMM 分块流式做，边算边维护 top-k，把 248320×4 B = 993 KB 的输出缓冲压到 64 KB。

第三，草稿头可以换小词表。MTP 的草稿 token 只需要在"可能被接受"的词上取样，8K 词表足够覆盖高频 token，草稿的 lm_head 从 1.049 GB 降到 34.59 MB（见 6.4 D6），这是投机解码从 1.66× 提到 2.29× 的关键一步。

## 11.4 视觉塔的量化

视觉塔 456.01 M 参数、470.15 MB，每张图只读一遍。它的算术强度 790 MAC/B，是算力受限而非带宽受限，所以量化的收益主要在容量与算力，不在带宽。

三个注意点：

1. `patch_embed` 是 conv3d 展开的 [1152, 1536]，输入是像素，动态范围最大；建议保持 FP16，只占 1.77 M 参数，省不下什么。
2. attention 的 head_dim 是 72，不是 32 的倍数。MXFP8 的 32 元素块在这里会切出 2 个整块加 1 个 8 元素残块，残块缩放因子只覆盖 8 个数，误差偏大；建议视觉 attention 用 8 或 16 元素的块。
3. GELU 有两个版本（block 内 tanh、merger 内 erf），量化误差在负半轴不同；merger 的输入是 4608 维拼接，建议保留 FP16 到 fc1 输出。

视觉塔的量化收益上限是 470.15 MB 的容量（占全模型 4.7%），对 12 GB/16 GB 设备的 KV 预算贡献有限；优先级低于文本主干。

---
---

# 第 12 章　对芯片设计的结论与待验证清单

## 12.1 九条结论

1. **decode 是纯带宽题，9B 的答案是 8.183 GB/token。** 310 GB/s 下上限 37.9 token/s，实际 22–26。相对 0.8B 慢 10.5 倍，慢得和权重大小成比例，没有额外损失（§8.2）。
2. **状态不再是常数。** 24 层 × 32 个 value 头 × 128×128 × FP32 = 50.33 MB，是 0.8B/2B 的 2.67 倍。单层 2 MiB 可驻 SRAM，全量必须落 DRAM，每 token 100.7 MB 流量（§4.3、§8.3）。
3. **KV 每 token 32,768 B，256K 时 8.59 GB。** 权重与 KV 的拐点在 250K 上下文，是家族里唯一"权重几乎永远主导"的一档；8 层 attention 在 256K 时的读 KV 时间（27.7 ms）超过全部权重（26.4 ms）（§6.2.2、§8.3）。
4. **lm_head 第一次不再是首位成本。** 1.017 GMAC / 1.049 GB，占 12.8%/12.8%（0.8B 是 33.1%，2B 是 26.8%）；但两份不共享的词表仍占容量 21.1%（§1.3、§8.1）。
5. **DeltaNet 内部出现 2:1 的 GQA。** 16 个 key 头对 32 个 value 头，`repeat_interleave` 带来 2 倍读放大（384 KB/token 的片上读，不落 DRAM）。这是 9B 与 0.8B/2B 唯一的结构性算子差异（§1.2、§5.6、§6.1 A⑩）。
6. **MLP 是最大的一块，也是最好优化的一块。** 4.832 GMAC（60.6%）、4.982 GB（60.9%），形状 4096↔12288，算术强度 0.97 MAC/B。引擎吞吐下限 302 GMAC/s，做到 300 就够 decode（§6.3、§8.2）。
7. **prefill 第一次成为体验瓶颈。** 1024 token 在 25 TOPS 上 573 ms，每 token 0.560 ms，等于 decode 单 token 的 1/47.2。TTFT 与 TPOT 的比例由 47.2 决定（§9.2）。
8. **视觉塔是算力受限的另一个极端。** 448² 图 371.53 GMAC、算术强度 790 MAC/B、算力时间是读权重的 19.6 倍；1 个视觉 token 只等于 0.27 个文本 token，图在 9B 上比在小模型上更便宜（§7.3）。
9. **真正的门槛是容量。** 全模型 MXFP8 9.952 GB：8 GB 装不下，12 GB 只剩 61K token 的 KV，16 GB 配 FP8 KV 才能覆盖 256K（14.30 GB）（§11.1、§8.3）。

## 12.2 三档对比：什么涨、什么不涨

| 量 | 0.8B | 2B | 9B | 规律 |
|---|---:|---:|---:|---|
| hidden | 1,024 | 2,048 | 4,096 | 每档 ×2 |
| 层数（总 / DeltaNet） | 24 / 18 | 24 / 18 | 32 / 24 | 9B ×1.33 |
| value 头 | 16 | 16 | 32 | 9B ×2 |
| 每 token 权重 | 0.776 GB | 1.941 GB | 8.183 GB | 随 hidden² |
| MAC/token | 0.767 G | 1.896 G | 7.976 G | 随 hidden² |
| 算术强度 | 0.99 | 0.98 | 0.97 | **不变** |
| 引擎下限 | 307 GMAC/s | 302 GMAC/s | 302 GMAC/s | **不变** |
| 单头状态 | 128 × 128 | 128 × 128 | 128 × 128 | **不变** |
| conv 窗口 | 4 | 4 | 4 | **不变** |
| 状态合计 | 18.87 MB | 18.87 MB | 50.33 MB | 头数 × 层数 |
| KV 每 token | 12,288 B | 12,288 B | 32,768 B | 层数 × KV 头 |
| lm_head 占 MAC | 33.1% | 26.8% | 12.8% | 单调下降 |
| decode 单 token | 2.50 ms | 6.26 ms | 26.40 ms | 线性于权重 |

涨的三样：层内 GEMM（hidden²）、层数、KV（层数 × KV 头）。不涨的三样：单头状态 128×128、卷积窗口 4、算术强度与引擎下限。9B 的特殊之处是把"状态不涨"这一条也打破了——不是几何变了，是并行度（value 头 ×2、层数 ×1.33）变了。

反向的一条：lm_head 的占比从 33.1% 降到 12.8%。小模型上"词表太贵"的直觉，在 9B 上不再成立；相反，词表的两份副本变成容量问题，而不是速度问题。

## 12.3 待验证清单

1. **权重文件本体未下载。** 19.3 GB 的分片只读了 header 与 index，全部形状、dtype、元素数来自 header；未做任何数值对拍。"形状对"不等于"权重对"。
2. **`modeling_qwen3_5.py` 取自 `transformers` main 分支，未锁定 commit。** 若发布版改了 delta 递推的 chunk 实现或门控顺序，MAC 不变但访存模式会变。
3. **视觉塔的 784/196 个 token 是推的。** 由 patch 16、merge 2、输入 448² 推得，未用真实 image processor 核对；若 `merge_temporal` 或 pad 规则不同，token 数会变。
4. **MTP 的接受率 α 无实测。** 1.66×（α=0.7）与 2.29×（α=0.7）依赖假设；α 每降 0.1，γ=3 的加速比降约 0.35。
5. **9B 的 prefill 是否走 chunked delta 未核到。** 本文按逐 token 递推计；若用 fla 的 chunk 模式，MAC 会上升（每 chunk 多两次 64×64×128 的 GEMM），访存更规整。
6. **310 GB/s 与 25 TOPS 是规划基线，不是实测。** 所有时间数字按这两个数折算，实际芯片的带宽效率与引擎利用率会各打 10–30% 折扣。
7. **`use_postshuffle_norm` 在 9B 上的取值未核到。** config 里有这个开关，但本文未确认其值；merger 的 norm 在 shuffle 前还是后，影响 4608/1152 两个维度的归一化位置。
8. **`A_log` 与 value 头的对应关系是推的。** 32 个 FP32 值对 32 个 value 头，由 shape 推得；源码里若有额外的广播或重排，递推的每头衰减率会不同。
9. **KV cache 的 FP8 方案未核到。** 16,384 B/token 的估算假设 k/v 各 1 B/元素且 per-head scale 可放；若需要 per-token scale，额外开销约 8 B/token/层。
10. **256K 上下文的 mRoPE 三组 position 在纯文本下的实际取值未核到。** 本文按"三组相同、退化为复制"处理；若推理框架对文本用了不同的 section 分配，长上下文的 attention 模式会变。

---
---

# 附录 A　符号表

| 符号 | 含义 | 本模型 |
|---|---|---|
| h | hidden size | 4096 |
| L | 总层数 | 32 |
| L_d | DeltaNet 层数 | 24 |
| H_q | attention 的 Q 头数 | 16 |
| H_kv | attention 的 KV 头数 | 4 |
| H_k | DeltaNet 的 key 头数 | 16 |
| H_v | DeltaNet 的 value 头数 | 32 |
| d_k / d_v | DeltaNet 的 key / value 头维 | 128 / 128 |
| D | attention 的 head_dim | 256 |
| D_rot | 参与 RoPE 的维数 | 64（32 个角度） |
| I | MLP 中间维 | 12288 |
| V | 词表大小 | 248320 |
| S | 上下文长度（已生成 token 数） | 4K / 32K / 256K |
| M | GEMM 的行数（token 数） | decode 时 1 |
| MAC | 一次乘加，折 2 FLOP | |
| MXFP8 | E4M3 + E8M0，块 32，8.25 bit/参数 | 1.031 B/参数 |
| AI | 算术强度，MAC/字节 | 0.97（本模型） |
| TTFT | 首 token 延迟 | 573 ms（1024 prompt） |
| TPOT | 每输出 token 时间 | 26.40 ms |

---

# 附录 B　复现脚本

纯 Python 3，无第三方依赖，`python3 app_b.py` 直接运行。所有数字与正文逐位一致。

```python
# app_b.py -- 纯 Python 3，无第三方依赖
h, L, Ld = 4096, 32, 24          # hidden / 总层数 / DeltaNet 层数
Hq, Hkv, Hv = 16, 4, 32          # Q 头 / KV 头 / DeltaNet value 头
dk, D = 128, 256                 # DeltaNet head_dim / attention head_dim
I, V, hv = 12288, 248320, 1152   # MLP 中间维 / 词表 / 视觉 hidden

A = [("input_layernorm", h), ("in_proj_qkv", h*(2*Hq*dk + Hv*dk)),
     ("in_proj_z", h*h), ("in_proj_a", h*Hv), ("in_proj_b", h*Hv),
     ("conv1d", (2*Hq*dk + Hv*dk)*4), ("A_log", Hv), ("dt_bias", Hv),
     ("norm", dk), ("out_proj", h*h), ("post_attention_layernorm", h),
     ("gate_proj", I*h), ("up_proj", I*h), ("down_proj", h*I)]
B = [("input_layernorm", h), ("q_proj", 2*h*h), ("k_proj", Hkv*D*h),
     ("v_proj", Hkv*D*h), ("o_proj", h*h), ("q_norm", D), ("k_norm", D),
     ("post_attention_layernorm", h),
     ("gate_proj", I*h), ("up_proj", I*h), ("down_proj", h*I)]

a, b = sum(x[1] for x in A), sum(x[1] for x in B)
emb = V*h
text = Ld*a + (L-Ld)*b + 2*emb + h

v_blk = (3*hv*hv + 3*hv) + (hv*hv + hv) + (4304*hv + 4304) \
        + (hv*4304 + hv) + 4*hv      # qkv/proj/fc1/fc2/两个 LN
vis = (hv*3*2*16*16 + hv) + 2304*hv + 27*v_blk \
      + 2304 + 4608*4608 + 4608 + 4096*4608 + 4096
mtp = b + h*2*h + 3*h
total = text + vis + mtp
print(f"A 层 {a:,}  x{Ld} = {Ld*a:,}")
print(f"B 层 {b:,}  x{L-Ld} = {(L-Ld)*b:,}")
print(f"文本主干 {text:,}")
print(f"视觉塔   {vis:,}")
print(f"MTP 头   {mtp:,}")
print(f"总计     {total:,}")

header, fbytes = 9653104368, 19306216416
print(f"header 元素和 {header:,}   差 {total-header}")
print(f"文件/2 {fbytes//2:,}   差 {fbytes//2-total:,}")
print(f"FP32 修正后 {(fbytes - Ld*(Hv+dk)*2)//2:,}")
print(f"MXFP8 容量 {total*1.031/1e9:.3f} GB")

wt = Ld*a + (L-Ld)*b + emb + h   # 每 token 读：无 embed 行、含 lm_head 与 final norm
m_a = 8192 + 33554432 + 16777216 + 262144 + 32768 + 128 + 8192 + 1572864 \
      + 16384 + 16777216 + 4096 + 100663296 + 12288 + 50331648 + 4096 + 8192
m_b = 8192 + 33554432 + 8388608 + 10240 + 2560 + 8192 + 16777216 + 4096 \
      + 151007232 + 4096 + 8192
tok = Ld*m_a + (L-Ld)*m_b + V*h
print(f"A 层 MAC {m_a:,}   B 层 MAC {m_b:,}")
print(f"每 token MAC {tok:,}  ({tok/1e9:.3f} GMAC)")
print(f"每 token 权重 {wt:,}  字节 {wt*1.031:,.0f}  算术强度 {tok/(wt*1.031):.4f}")
print(f"310 GB/s 上限 {310e9/(wt*1.031):.2f} token/s")
```

实际输出：

```
A 层 218,407,104  x24 = 5,241,770,496
B 层 209,723,904  x8 = 1,677,791,232
文本主干 8,953,803,264
视觉塔   456,010,480
MTP 头   243,290,624
总计     9,653,104,368
header 元素和 9,653,104,368   差 0
文件/2 9,653,108,208   差 3,840
FP32 修正后 9,653,104,368
MXFP8 容量 9.952 GB
A 层 MAC 220,033,152   B 层 MAC 209,773,056
每 token MAC 7,976,098,816  (7.976 GMAC)
每 token 权重 7,936,684,544  字节 8,182,721,765  算术强度 0.9747
310 GB/s 上限 37.88 token/s
```

与 header 的差异有两层，都对得上。

第一层：脚本逐张量算出的 9,653,104,368 与 header 里 775 个张量的元素数之和**逐位相同，差 0**。

第二层：权重文件 19,306,216,416 B ÷ 2 = 9,653,108,208，比脚本多 3,840。原因是 24 层的 `A_log`（32 个）与 `linear_attn.norm`（128 个）是 FP32，共 24 × 160 = 3,840 个元素，每个在文件里比 BF16 多占 2 B，合计 7,680 B。脚本第 37 行把这 7,680 B 从文件字节里扣掉再 ÷2，得到 9,653,104,368，与元素数之和完全一致。这就是正文 1.1 说的"差额来源已定位到具体张量"。

正文 8.2 的字节合计 8,182,721,764 是逐行取整后相加的结果，脚本的精确乘积是 8,182,721,765，差 1 B，不影响 8.183 GB、26.40 ms 与 37.9 token/s 中的任何一个。
