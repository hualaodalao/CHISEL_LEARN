PROFILE_NAME = "workerbee_default_v4"
GEM5_RELEASE = "25.1.0.1"
GEM5_COMMIT = "c8222cc67a399bfc01e8658dd14b30d5bfd634f9"

DEFAULT_PROFILE = {
    "name": PROFILE_NAME,
    "spec_version": "4.0.0",
    "parameters": {
        "arrayN": 8,
        "clusterM": 2,
        "aW": 16,
        "bW": 16,
        "cW": 0,
        "aBufferDepth": 64,
        "bBufferDepth": 64,
        "cBufferDepth": 64,
        "addrWidth": 32,
        "scaleBufferDepth": 64,
        "cDmaExtW": 256,
        "supportedFmts": [
            "FP8E4M3", "FP8E5M2", "INT4", "INT8", "MXE2M1", "MXE4M3", "MXE5M2",
        ],
    },
    "derived": {
        "totalN": 16,
        "aExtW": 256,
        "bExtW": 256,
        "cEffW": 40,
        "cExtW": 640,
        "scaleAW": 32,
        "scaleBW": 32,
        "cScaleRowW": 8,
        "cBeatsPerRowMax": 2,
        "mnkWidth": 16,
    },
    "calibration": "pending",
}

DMA_PORTS = ("dma_a", "dma_b", "dma_c", "dma_scale_a", "dma_scale_b", "dma_scale_c")


def verify_elaboration(profile):
    expected = {key: DEFAULT_PROFILE[key] for key in ("parameters", "derived")}
    if profile != expected:
        raise ValueError("Elaborated WorkerBeeConfig does not match the frozen P0 profile")
