#!/usr/bin/env bash
# Dựng máy chủ Vào Hang lên Cloud Run, và Cloud Scheduler gọi /v1/admin/tick mỗi phút.
# Chạy trong Cloud Shell (đã đăng nhập sẵn), chạy lại bao nhiêu lần cũng được:
#
#   git clone -b claude/exciting-ramanujan-mptsqi https://github.com/hoainam9212/mu-agent
#   bash mu-agent/deploy/cloudrun.sh <project-id>
#
# Script hỏi chuỗi kết nối database và token football-data.org (gõ không hiện ra màn hình),
# tự sinh ADMIN_TOKEN, rồi in địa chỉ máy chủ.
set -euo pipefail

PROJECT="${1:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-asia-northeast1}"  # Tokyo
SERVICE=vaohang
RUN_SA="vaohang-run@${PROJECT}.iam.gserviceaccount.com"
HERE="$(cd "$(dirname "$0")/.." && pwd)"

[ -n "$PROJECT" ] || { echo "Thiếu project id: bash deploy/cloudrun.sh <project-id>"; exit 1; }
gcloud config set project "$PROJECT" >/dev/null
NUMBER="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
echo "== Project $PROJECT ($NUMBER), vùng $REGION"

echo "== Bật các dịch vụ cần dùng (lần đầu mất 1-2 phút)"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com \
  cloudscheduler.googleapis.com fcm.googleapis.com compute.googleapis.com iam.googleapis.com

echo "== Tài khoản chạy máy chủ, chỉ có quyền gửi thông báo FCM"
gcloud iam service-accounts describe "$RUN_SA" >/dev/null 2>&1 ||
  gcloud iam service-accounts create vaohang-run --display-name "Vào Hang (Cloud Run)"
gcloud projects add-iam-policy-binding "$PROJECT" --member "serviceAccount:$RUN_SA" \
  --role roles/firebasecloudmessaging.admin --condition None >/dev/null
# Cloud Build dùng tài khoản compute mặc định để build image từ mã nguồn.
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member "serviceAccount:${NUMBER}-compute@developer.gserviceaccount.com" \
  --role roles/run.builder --condition None >/dev/null ||
  echo "(Không gắn được quyền run.builder; nếu bước build lỗi quyền, chạy lại script sau 1 phút.)"

echo
read -rsp "Chuỗi kết nối database (Supabase → Connect → Session pooler): " DATABASE_URL; echo
read -rsp "Token football-data.org (Enter để bỏ qua, nhập kết quả bằng tay): " FOOTBALL_DATA_TOKEN; echo
read -rsp "Key Claude (Enter để bỏ qua, dùng kho câu có sẵn): " ANTHROPIC_API_KEY; echo
[ -n "$DATABASE_URL" ] || { echo "Cần chuỗi kết nối database."; exit 1; }
case "$DATABASE_URL" in *YOUR-PASSWORD*) echo "Chuỗi còn [YOUR-PASSWORD]: thay bằng mật khẩu database."; exit 1;; esac

if command -v psql >/dev/null; then
  echo "== Thử kết nối database"
  psql "$DATABASE_URL" -qtAc 'select 1' >/dev/null || { echo "Không kết nối được database."; exit 1; }
fi

ADMIN_TOKEN="$(openssl rand -hex 24)"
ENV_FILE="$(mktemp)"
chmod 600 "$ENV_FILE"
trap 'rm -f "$ENV_FILE"' EXIT
yaml() { printf '%s: "%s"\n' "$1" "$(printf '%s' "$2" | sed 's/\\/\\\\/g; s/"/\\"/g')"; }
{
  yaml DATABASE_URL "$DATABASE_URL"
  yaml ADMIN_TOKEN "$ADMIN_TOKEN"
  yaml FCM_ENABLED 1
  yaml POLLER_ENABLED 0   # Cloud Run chỉ cấp CPU khi có request → Cloud Scheduler gọi tick
  yaml GOOGLE_CLOUD_PROJECT "$PROJECT"
  [ -z "$FOOTBALL_DATA_TOKEN" ] || yaml FOOTBALL_DATA_TOKEN "$FOOTBALL_DATA_TOKEN"
  [ -z "$ANTHROPIC_API_KEY" ] || yaml ANTHROPIC_API_KEY "$ANTHROPIC_API_KEY"
} >"$ENV_FILE"

echo "== Build và chạy máy chủ (3-5 phút)"
# max-instances 1: chỉ một bản chạy, để mỗi kết quả trận chỉ xử lý và báo một lần.
gcloud run deploy "$SERVICE" --source "$HERE/server" --region "$REGION" \
  --service-account "$RUN_SA" --allow-unauthenticated \
  --min-instances 0 --max-instances 1 --cpu 1 --memory 512Mi --timeout 120 \
  --env-vars-file "$ENV_FILE" --quiet
URL="$(gcloud run services describe "$SERVICE" --region "$REGION" --format='value(status.url)')"

echo "== Cloud Scheduler gọi tick mỗi phút"
JOB=(--location "$REGION" --schedule "* * * * *" --time-zone "Etc/UTC" --uri "$URL/v1/admin/tick"
     --http-method POST --headers "X-Admin-Token=$ADMIN_TOKEN" --attempt-deadline 120s)
# Tạo lại job mỗi lần chạy vì ADMIN_TOKEN mới.
gcloud scheduler jobs delete vaohang-tick --location "$REGION" --quiet >/dev/null 2>&1 || true
gcloud scheduler jobs create http vaohang-tick "${JOB[@]}" --quiet >/dev/null

echo "== Kiểm tra"
curl -fsS "$URL/healthz"; echo
curl -fsS -X POST -H "X-Admin-Token: $ADMIN_TOKEN" "$URL/v1/admin/tick"; echo
curl -fsS -H "X-Admin-Token: $ADMIN_TOKEN" "$URL/v1/admin/status"; echo

cat <<EOF

Xong. Máy chủ: $URL
ADMIN_TOKEN (giữ riêng, dùng cho lệnh của người trực): $ADMIN_TOKEN
EOF
