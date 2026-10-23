#!/usr/bin/env bash
#
# distrust-emudhra.sh — remove the compromised eMudhra/emSign trust anchors
# (and, where safe, the Sectigo "AAA Certificate Services" cross-sign root)
# from a JDK's cacerts trust store.
#
# This is ROOT-level distrust: it deletes the trust anchors, so both the direct
# emSign chains and the Sectigo cross-signed chains collapse — and, unlike a
# blocked.certs fingerprint, it is NOT defeated by re-encoding the intermediate
# (see Sonatype, "The limits of fingerprint blocklists" [1]).
#
# [1]: https://www.sonatype.com/blog/the-limits-of-fingerprint-blocklists-a-reproducible-java-certificate-bypass
# Usage:
#   distrust-emudhra.sh [--dry-run] [JAVA_HOME]
#     JAVA_HOME defaults to $JAVA_HOME, then to the JDK that `java` resolves to.
#     --dry-run   report what would be removed; make no changes (also via DRY_RUN=1)
#
# Env:
#   STOREPASS   cacerts password (default: changeit)
#   FORCE_AAA   set to 1 to remove AAA even if USERTrust is absent (may break
#               legitimate Sectigo chains that rely on the AAA fallback)
#   DRY_RUN     set to 1 for a no-change preview (same as --dry-run)
#
# Exit codes: 0 ok (changes applied, already clean, or dry-run); 1 error.
set -o errexit   # exit immediately if any command fails (short: -e)
set -o nounset   # treat use of an unset variable as an error (short: -u)
set -o pipefail  # a pipeline fails if any stage fails, not just the last

log()  { printf '%s\n' "$*"; }
warn() { if [ -n "${GITHUB_ACTIONS:-}" ]; then printf '::warning::%s\n' "$*"; else printf 'WARN: %s\n' "$*" >&2; fi; }
err()  { if [ -n "${GITHUB_ACTIONS:-}" ]; then printf '::error::%s\n'   "$*"; else printf 'ERROR: %s\n' "$*" >&2; fi; }

STOREPASS="${STOREPASS:-changeit}"
FORCE_AAA="${FORCE_AAA:-0}"
DRY_RUN="${DRY_RUN:-0}"

# --- args (optional --dry-run, optional JAVA_HOME) ----------------------------
ARG_JH=""
for a in "$@"; do
  case "$a" in
    --dry-run) DRY_RUN=1 ;;
    *)         ARG_JH="$a" ;;
  esac
done
[ "$DRY_RUN" = "1" ] && log "(dry run — no changes will be made)"

# --- locate JAVA_HOME ---------------------------------------------------------
# If JAVA_HOME is unset we accept the dir
# that holds `java` without canonicalizing symlinks; pass JAVA_HOME explicitly
# if your `java` is itself a wrapper symlink elsewhere.
JH="${ARG_JH:-${JAVA_HOME:-}}"
if [ -z "$JH" ] && command -v java >/dev/null 2>&1; then
  JH="$(cd "$(dirname "$(command -v java)")/.." && pwd)"
fi
[ -n "$JH" ] && [ -d "$JH" ] || { err "JAVA_HOME not found (pass it as an argument)"; exit 1; }

# Pin keytool to this JDK rather than $PATH: under sudo, root's PATH often omits
# a hostedtoolcache JDK, and we want the keytool that matches the store anyway.
if [ -x "$JH/bin/keytool" ]; then
  KEYTOOL="$JH/bin/keytool"
elif command -v keytool >/dev/null 2>&1; then
  KEYTOOL="keytool"
else
  err "keytool not found in $JH/bin or on PATH"; exit 1
fi

# --- locate cacerts (JDK 8 uses jre/lib; newer uses lib) ----------------------
KS=""
for c in "$JH/lib/security/cacerts" "$JH/jre/lib/security/cacerts"; do
  [ -f "$c" ] && { KS="$c"; break; }
done
[ -n "$KS" ] || { err "cacerts not found under $JH"; exit 1; }
# Operate on the path as-is. If it is a symlink (distro-managed store), keytool
# edits the symlink target, which is exactly what we want.
log "JAVA_HOME:   $JH"
log "trust store: $KS"

# A hostedtoolcache JDK often has a root-owned, read-only cacerts: reads work
# but keytool -delete fails with "Permission denied". Catch it early with a
# clear hint instead of a cryptic keytool error mid-run.
if [ "$DRY_RUN" != "1" ] && [ ! -w "$KS" ]; then
  err "trust store is not writable by $(id -un): re-run this step with sudo (e.g. 'sudo -E bash $0')"
  exit 1
fi

# --- decide whether removing AAA is safe --------------------------------------
# AAA = Sectigo "AAA Certificate Services", the cross-sign path for the eMudhra
# intermediates. Modern Sectigo chains lead to USERTrust instead; only remove
# AAA when USERTrust is present to carry those chains (or when FORCE_AAA=1).
REMOVE_AAA=1
if [ "$FORCE_AAA" != "1" ]; then
  if ! "$KEYTOOL" -list -keystore "$KS" -storepass "$STOREPASS" 2>/dev/null | grep -qi usertrust; then
    warn "USERTrust not in trust store; keeping AAA so Sectigo chains still validate (set FORCE_AAA=1 to override)"
    REMOVE_AAA=0
  fi
fi

# --- collect aliases to delete, matched by Owner (names vary by vendor) -------
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
"$KEYTOOL" -list -v -keystore "$KS" -storepass "$STOREPASS" 2>/dev/null \
  | awk -F': ' -v rmaaa="$REMOVE_AAA" '
      /^Alias name:/ { a = $2 }
      /^Owner:/ {
        if ($0 ~ /emSign/)                                      print a;
        else if ($0 ~ /AAA Certificate Services/ && rmaaa=="1") print a;
      }' \
  | sort -u > "$TMP"

if [ ! -s "$TMP" ]; then
  log "nothing to remove (no emSign entries; AAA absent or intentionally kept)"
elif [ "$DRY_RUN" = "1" ]; then
  log "would remove:"
  while IFS= read -r alias; do [ -n "$alias" ] && log "  $alias"; done < "$TMP"
else
  cp "$KS" "$KS.bak.$(date +%Y%m%d%H%M%S)"
  while IFS= read -r alias; do
    [ -n "$alias" ] || continue
    "$KEYTOOL" -delete -alias "$alias" -keystore "$KS" -storepass "$STOREPASS"
    log "removed: $alias"
  done < "$TMP"
fi

# --- report remaining -----------------------------------------------------
log "remaining emSign/AAA entries (none expected, unless AAA deliberately kept):"
if "$KEYTOOL" -list -keystore "$KS" -storepass "$STOREPASS" 2>/dev/null \
     | grep -iE 'emsign|aaa certificate|comodoaaa'; then :; else log "  (none)"; fi
