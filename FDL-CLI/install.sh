#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# Faa YDL - FDL CLI Installer
# ============================================================

APP_NAME="fdl"
REPO_URL="https://raw.githubusercontent.com/FaaRamadhann/Faa-YDL/refs/heads/main/FDL-CLI/fdl"
INSTALL_DIR="${HOME}/.local/bin"
INSTALL_PATH="${INSTALL_DIR}/${APP_NAME}"
TMP_PATH="${INSTALL_PATH}.tmp"

# ------------------------------------------------------------
# Colors
# ------------------------------------------------------------

if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    RESET='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    BOLD=''
    RESET=''
fi

# ------------------------------------------------------------
# Helpers
# ------------------------------------------------------------

info() {
    printf "${BLUE}[INFO]${RESET} %s\n" "$*"
}

success() {
    printf "${GREEN}[ OK ]${RESET} %s\n" "$*"
}

warn() {
    printf "${YELLOW}[WARN]${RESET} %s\n" "$*"
}

error() {
    printf "${RED}[FAIL]${RESET} %s\n" "$*" >&2
}

cleanup() {
    rm -f "$TMP_PATH"
}

trap cleanup EXIT

die() {
    error "$*"
    exit 1
}

# ------------------------------------------------------------
# Header
# ------------------------------------------------------------

printf "\n"
printf "${CYAN}${BOLD}"
printf "========================================\n"
printf "        FDL CLI INSTALLER\n"
printf "        Faa YDL Project\n"
printf "========================================\n"
printf "${RESET}\n"

# ------------------------------------------------------------
# Environment checks
# ------------------------------------------------------------

info "Checking environment..."

if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
    warn "Running as root."
    warn "Installation will use /root/.local/bin."
fi

if command -v curl >/dev/null 2>&1; then
    DOWNLOADER="curl"
elif command -v wget >/dev/null 2>&1; then
    DOWNLOADER="wget"
else
    die "curl or wget is required."
fi

success "Downloader: ${DOWNLOADER}"

# ------------------------------------------------------------
# Create install directory
# ------------------------------------------------------------

mkdir -p "$INSTALL_DIR" || die "Cannot create ${INSTALL_DIR}"

success "Install directory: ${INSTALL_DIR}"

# ------------------------------------------------------------
# Existing installation
# ------------------------------------------------------------

if [[ -f "$INSTALL_PATH" ]]; then
    warn "${APP_NAME} is already installed."

    BACKUP_PATH="${INSTALL_PATH}.backup.$(date +%Y%m%d-%H%M%S)"

    mv "$INSTALL_PATH" "$BACKUP_PATH" \
        || die "Failed to backup existing installation."

    success "Previous installation backed up:"
    printf "       %s\n" "$BACKUP_PATH"
fi

# ------------------------------------------------------------
# Download
# ------------------------------------------------------------

info "Downloading ${APP_NAME}..."

if [[ "$DOWNLOADER" == "curl" ]]; then
    curl \
        --fail \
        --silent \
        --show-error \
        --location \
        --retry 3 \
        --connect-timeout 10 \
        --max-time 120 \
        "$REPO_URL" \
        -o "$TMP_PATH"
else
    wget \
        --quiet \
        --show-progress \
        --tries=3 \
        --timeout=10 \
        -O "$TMP_PATH" \
        "$REPO_URL"
fi

[[ -s "$TMP_PATH" ]] || die "Downloaded file is empty."

success "Download completed."

# ------------------------------------------------------------
# Basic file validation
# ------------------------------------------------------------

info "Validating downloaded file..."

if ! head -n 1 "$TMP_PATH" | grep -qE '^#!.*(python|bash|sh|env)'; then
    warn "File does not appear to have a standard shebang."
    warn "Continuing because the project may use another executable format."
fi

# ------------------------------------------------------------
# Atomic install
# ------------------------------------------------------------

chmod 755 "$TMP_PATH" || die "Failed to set executable permission."

mv "$TMP_PATH" "$INSTALL_PATH" \
    || die "Failed to install ${APP_NAME}."

success "Installed: ${INSTALL_PATH}"

# ------------------------------------------------------------
# PATH check
# ------------------------------------------------------------

printf "\n"
info "Checking PATH..."

case ":${PATH}:" in
    *":${INSTALL_DIR}:"*)
        success "${INSTALL_DIR} is already in PATH."
        PATH_OK=1
        ;;
    *)
        PATH_OK=0
        warn "${INSTALL_DIR} is not currently in PATH."
        ;;
esac

# ------------------------------------------------------------
# Shell configuration
# ------------------------------------------------------------

if [[ "$PATH_OK" -eq 0 ]]; then
    SHELL_NAME="$(basename "${SHELL:-}")"

    case "$SHELL_NAME" in
        bash)
            SHELL_RC="${HOME}/.bashrc"
            ;;
        zsh)
            SHELL_RC="${HOME}/.zshrc"
            ;;
        fish)
            SHELL_RC="${HOME}/.config/fish/config.fish"
            ;;
        *)
            SHELL_RC="${HOME}/.profile"
            ;;
    esac

    PATH_LINE='export PATH="$HOME/.local/bin:$PATH"'

    if [[ "$SHELL_NAME" == "fish" ]]; then
        PATH_LINE='fish_add_path "$HOME/.local/bin"'
    fi

    if [[ ! -f "$SHELL_RC" ]] || ! grep -Fq '.local/bin' "$SHELL_RC"; then
        printf "\n%s\n" "$PATH_LINE" >> "$SHELL_RC"
        success "Added ${INSTALL_DIR} to ${SHELL_RC}"
    else
        success "PATH configuration already exists in ${SHELL_RC}"
    fi

    warn "Restart your terminal or run:"
    printf "       source %s\n" "$SHELL_RC"
fi

# ------------------------------------------------------------
# Verify executable
# ------------------------------------------------------------

printf "\n"
info "Verifying installation..."

if [[ -x "$INSTALL_PATH" ]]; then
    success "${APP_NAME} is executable."
else
    die "${APP_NAME} is not executable."
fi

# ------------------------------------------------------------
# Version / help test
# ------------------------------------------------------------

if [[ "$PATH_OK" -eq 1 ]]; then
    if "$APP_NAME" --help >/dev/null 2>&1; then
        success "${APP_NAME} --help test passed."
    else
        warn "${APP_NAME} installed, but --help test failed."
        warn "Check the script manually with:"
        printf "       %s --help\n" "$INSTALL_PATH"
    fi
else
    if "$INSTALL_PATH" --help >/dev/null 2>&1; then
        success "${APP_NAME} --help test passed."
    else
        warn "${APP_NAME} installed, but --help test failed."
    fi
fi

# ------------------------------------------------------------
# Final output
# ------------------------------------------------------------

printf "\n"
printf "${GREEN}${BOLD}"
printf "========================================\n"
printf "          INSTALLATION COMPLETE\n"
printf "========================================\n"
printf "${RESET}\n"

printf "Command : ${CYAN}%s${RESET}\n" "$APP_NAME"
printf "Path    : ${CYAN}%s${RESET}\n" "$INSTALL_PATH"

printf "\n"

if [[ "$PATH_OK" -eq 0 ]]; then
    printf "${YELLOW}Next step:${RESET}\n"
    printf "  ${CYAN}source %s${RESET}\n" "$SHELL_RC"
    printf "\n"
fi

printf "${GREEN}Run:${RESET}\n"
printf "  ${CYAN}%s --help${RESET}\n" "$APP_NAME"
printf "\n"
