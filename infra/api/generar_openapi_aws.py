"""
Genera pedidos360-openapi-aws.yaml (registro en AWS API Gateway - HTTP API) a partir del
contrato base pedidos360-openapi.yaml, agregando las extensiones de AWS:

  - x-amazon-apigateway-integration: HTTP_PROXY hacia el backend (gateway nginx de la VM/EC2)
  - x-amazon-apigateway-authorizer:  JWT authorizer de Amazon Cognito en cada ruta
  - x-amazon-apigateway-cors:        CORS (API Gateway responde los preflight OPTIONS)

Uso:  pip install pyyaml  &&  python generar_openapi_aws.py
Los marcadores IP_BACKEND, REGION, USER_POOL_ID, CLIENT_ID_COGNITO y ORIGEN_FRONTEND se reemplazan
a mano (o con buscar/reemplazar) antes de importar el archivo.
"""
from pathlib import Path

import yaml

AQUI = Path(__file__).parent
BASE = yaml.safe_load((AQUI / "pedidos360-openapi.yaml").read_text(encoding="utf-8"))
PARAMETROS = BASE["components"]["parameters"]
METODOS = ("get", "post", "put", "patch", "delete")


def resolver(parametro):
    if "$ref" in parametro:
        return dict(PARAMETROS[parametro["$ref"].split("/")[-1]])
    return dict(parametro)


rutas = {}
for ruta, operaciones in BASE["paths"].items():
    ruta_aws = "/api" + ruta
    rutas[ruta_aws] = {}
    for metodo, op in operaciones.items():
        if metodo not in METODOS:
            continue
        nueva = {
            "operationId": op["operationId"],
            "summary": op.get("summary", ""),
            "tags": op.get("tags", []),
            "security": [{"cognito": []}],
            "x-amazon-apigateway-integration": {
                "type": "http_proxy",
                "httpMethod": metodo.upper(),
                "uri": "http://IP_BACKEND:8080" + ruta_aws,
                "payloadFormatVersion": "1.0",
                "connectionType": "INTERNET",
            },
        }
        if op.get("parameters"):
            nueva["parameters"] = [resolver(p) for p in op["parameters"]]
        rutas[ruta_aws][metodo] = nueva

aws = {
    "openapi": "3.0.1",
    "info": {
        "title": "pedidos360-api",
        "version": BASE["info"]["version"],
        "description": "Pedidos 360 en AWS API Gateway (HTTP API). Generado desde pedidos360-openapi.yaml.",
    },
    "x-amazon-apigateway-cors": {
        "allowOrigins": ["ORIGEN_FRONTEND", "http://localhost:4200"],
        "allowMethods": ["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
        "allowHeaders": ["authorization", "content-type"],
        "exposeHeaders": ["location"],
        "maxAge": 600,
    },
    "paths": rutas,
    "components": {
        "securitySchemes": {
            "cognito": {
                "type": "oauth2",
                "flows": {},
                "x-amazon-apigateway-authorizer": {
                    "type": "jwt",
                    "identitySource": "$request.header.Authorization",
                    "jwtConfiguration": {
                        "issuer": "https://cognito-idp.REGION.amazonaws.com/USER_POOL_ID",
                        "audience": ["CLIENT_ID_COGNITO"],
                    },
                },
            }
        }
    },
}

encabezado = "# GENERADO por generar_openapi_aws.py desde pedidos360-openapi.yaml. No editar a mano.\n"
(AQUI / "pedidos360-openapi-aws.yaml").write_text(
    encabezado + yaml.safe_dump(aws, sort_keys=False, allow_unicode=True, width=120), encoding="utf-8")
print(f"OK: {sum(len(v) for v in rutas.values())} operaciones en {len(rutas)} rutas")
