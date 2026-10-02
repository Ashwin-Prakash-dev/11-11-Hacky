#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_NAME="${DEEPSIGHT_ENV_NAME:-deepsight}"
INSTALL_DEPS=1
DOWNLOAD_MODELS=1
INCLUDE_UNVERIFIED=0

usage() {
  echo "Usage: scripts/setup_dev.sh [options]"
  echo
  echo "Creates or updates the DeepSight Conda environment and downloads pinned models."
  echo
  echo "Options:"
  echo "  --env-name NAME         Conda environment name (default: deepsight)"
  echo "  --skip-deps             Do not install ml/requirements.txt"
  echo "  --skip-models           Do not download approved evaluation models"
  echo "  --include-unverified    Also download explicitly unverified candidates"
  echo "  -h, --help              Show this help"
}

while (($#)); do
  case "$1" in
    --env-name)
      [[ $# -ge 2 ]] || { echo "--env-name needs a value" >&2; exit 2; }
      ENV_NAME="$2"
      shift 2
      ;;
    --skip-deps)
      INSTALL_DEPS=0
      shift
      ;;
    --skip-models)
      DOWNLOAD_MODELS=0
      shift
      ;;
    --include-unverified)
      INCLUDE_UNVERIFIED=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

command -v conda >/dev/null 2>&1 || {
  echo "Conda is required. Install Miniconda or Miniforge first." >&2
  exit 1
}

if ! conda run -n "$ENV_NAME" python --version >/dev/null 2>&1; then
  conda create -y -n "$ENV_NAME" -c conda-forge python=3.12 pip
fi

if ((INSTALL_DEPS)); then
  conda run -n "$ENV_NAME" python -m pip install -r "$REPO_ROOT/ml/requirements.txt"
fi

if ((DOWNLOAD_MODELS)); then
  MODEL_ARGS=(
    --manifest "$REPO_ROOT/ml/models.json"
    --output-dir "$REPO_ROOT/ml/models"
  )
  if ((INCLUDE_UNVERIFIED)); then
    MODEL_ARGS+=(--include-unverified)
  fi
  conda run -n "$ENV_NAME" python "$REPO_ROOT/ml/tools/download_models.py" "${MODEL_ARGS[@]}"
fi

echo
echo "DeepSight development environment is ready."
echo "Activate it with: conda activate $ENV_NAME"
