#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Prueba de humo post-despliegue. La usan los dos entornos del pipeline
# (Docker Compose y Kubernetes).
#
# Uso: bash scripts/smoke-test.sh <BASE_URL> [COMMIT_ESPERADO]
#   - Espera a que el servicio responda sano.
#   - Verifica que la versión desplegada corresponde al commit esperado (trazabilidad).
#   - Ejecuta un flujo CRUD real contra la API.
# -----------------------------------------------------------------------------
set -euo pipefail

BASE_URL="${1:?Uso: smoke-test.sh <BASE_URL> [COMMIT_ESPERADO]}"
COMMIT_ESPERADO="${2:-}"
API="$BASE_URL/api/v1/productos"

ok()    { echo "  [OK]  $*"; }
falla() { echo "  [FALLA] $*" >&2; exit 1; }

echo "==> Esperando a que $BASE_URL esté disponible..."
for i in $(seq 1 40); do
  if curl -fsS "$BASE_URL/actuator/health" 2>/dev/null | grep -q '"UP"'; then
    ok "Servicio sano (intento $i)"
    break
  fi
  [ "$i" -eq 40 ] && falla "El servicio no respondió sano a tiempo"
  sleep 3
done

echo "==> Verificando trazabilidad (/actuator/info)"
INFO=$(curl -fsS "$BASE_URL/actuator/info")
echo "$INFO" | jq .
COMMIT_DESPLEGADO=$(echo "$INFO" | jq -r '.app.commit')
if [ -n "$COMMIT_ESPERADO" ]; then
  [ "$COMMIT_DESPLEGADO" = "$COMMIT_ESPERADO" ] \
    || falla "Commit desplegado ($COMMIT_DESPLEGADO) distinto al esperado ($COMMIT_ESPERADO)"
  ok "La versión desplegada corresponde al commit $COMMIT_ESPERADO"
fi

echo "==> Flujo funcional de la API"
SKU="SMOKE-$(date +%s)-$RANDOM"

RESP=$(curl -sS -w '\n%{http_code}' -X POST "$API" -H 'Content-Type: application/json' \
  -d "{\"sku\":\"$SKU\",\"nombre\":\"Producto smoke test\",\"precio\":1000,\"stock\":5}")
CODIGO=$(echo "$RESP" | tail -n1)
CUERPO=$(echo "$RESP" | sed '$d')
[ "$CODIGO" = "201" ] || falla "POST esperaba 201 y recibió $CODIGO"
ID=$(echo "$CUERPO" | jq -r '.id')
ok "POST crea producto (id=$ID)"

curl -fsS "$API/$ID" | jq -e --arg sku "$SKU" '.sku == $sku' >/dev/null || falla "GET no devuelve el producto"
ok "GET obtiene el producto"

curl -fsS -X PATCH "$API/$ID/stock" -H 'Content-Type: application/json' -d '{"cantidad":-2}' \
  | jq -e '.stock == 3' >/dev/null || falla "PATCH de stock no descontó correctamente"
ok "PATCH descuenta stock"

CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$API/$ID/stock" \
  -H 'Content-Type: application/json' -d '{"cantidad":-100}')
[ "$CODIGO" = "409" ] || falla "Stock negativo debía responder 409 y respondió $CODIGO"
ok "Regla de negocio: stock negativo rechazado (409)"

CODIGO=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API/$ID")
[ "$CODIGO" = "204" ] || falla "DELETE esperaba 204 y recibió $CODIGO"
ok "DELETE elimina el producto"

echo "==> Prueba de humo superada"
