"""Downloads the Gemma 3n E4B int4 .task (MediaPipe) into ./model.
Resumable: re-run to continue a partial download. Token read from HF_TOKEN env.
"""
import os
from huggingface_hub import hf_hub_download

REPO = "google/gemma-3n-E4B-it-litert-preview"
FILE = "gemma-3n-E4B-it-int4.task"
# Downloads into a ./model folder next to this script (override with MODEL_DIR).
DEST = os.environ.get("MODEL_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "model"))

path = hf_hub_download(
    repo_id=REPO,
    filename=FILE,
    local_dir=DEST,
    token=os.environ["HF_TOKEN"],
)
size_gb = os.path.getsize(path) / (1024 ** 3)
print(f"DOWNLOADED: {path}  ({size_gb:.2f} GB)")
