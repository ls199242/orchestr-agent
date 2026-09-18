import os
import httpx
from fastapi import FastAPI, Request, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from typing import Dict, Any, Optional

from config_store import config_store

app = FastAPI(title="OrchestrAgent Mock Console", version="1.0.0")

# 启用跨域支持
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

try:
    import config
    JAVA_BACKEND_URL = getattr(config, "JAVA_BACKEND_URL", os.getenv("JAVA_BACKEND_URL", "http://127.0.0.1:8080"))
    SERVER_HOST = getattr(config, "SERVER_HOST", "0.0.0.0")
    SERVER_PORT = getattr(config, "SERVER_PORT", 8000)
except ImportError:
    JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://127.0.0.1:8080")
    SERVER_HOST = "0.0.0.0"
    SERVER_PORT = 8000


# ==========================================
# 1. 供 Java OrchestrAgent 7 大仓储调用的 Mock API
# ==========================================

def _mng_resp(data: Any):
    return {
        "result": True,
        "message": "success",
        "errorCode": None,
        "data": data,
    }


@app.get("/api/strategy")
def get_strategy_configs():
    return _mng_resp(config_store.get_category("strategies"))


@app.get("/api/agent")
def get_agent_configs():
    return _mng_resp(config_store.get_category("agents"))


@app.get("/api/tool")
def get_tool_configs():
    return _mng_resp(config_store.get_category("tools"))


@app.get("/api/model")
def get_model_configs():
    return _mng_resp(config_store.get_category("models"))


@app.get("/api/dict")
def get_dict_configs():
    return _mng_resp(config_store.get_category("dict"))


# ==========================================
# 2. 仿真业务工具 Mock 端点
# ==========================================

@app.post("/mock/tools/weather")
def mock_weather_tool(payload: Dict[str, Any]):
    city = payload.get("city", "上海")
    airport_code = payload.get("airportCode", "SHA")
    # 模拟真实落地夜间下雨场景
    return {
        "city": city,
        "airportCode": airport_code,
        "weather": "RAINY",
        "temperature": 18.5,
        "isNight": True,
        "description": "夜间阴有中雨，气温 18.5°C，东风 3 级，路面湿滑，建议提前规划接驳交通"
    }


@app.post("/mock/tools/user-profile")
def mock_user_profile_tool(payload: Dict[str, Any]):
    user_id = payload.get("userId", "U8801")
    return {
        "userId": user_id,
        "preferenceType": "PRICE_FIRST",
        "historicalSpendingLevel": "LOW",
        "frequentHotelStar": 3,
        "tags": ["价格敏感", "高性价比偏好", "偏好拼车/特惠接送"]
    }


@app.post("/mock/tools/arrival-services")
def mock_arrival_services_tool(payload: Dict[str, Any]):
    airport_code = payload.get("airportCode", "SHA")
    return {
        "airportCode": airport_code,
        "pickupServices": [
            {
                "serviceId": "pickup_car_economy_01",
                "carType": "ECONOMY",
                "title": "虹桥机场直达专车 · 经济特惠型",
                "price": 58.0,
                "features": "即走免等、雨夜特惠、比现场打车便宜 25%"
            },
            {
                "serviceId": "pickup_car_comfort_02",
                "carType": "COMFORT",
                "title": "虹桥机场尊享接机 · 舒适商务型",
                "price": 168.0,
                "features": "司机举牌迎接、免费等待60分钟、宽敞静音座驾"
            }
        ],
        "hotels": [
            {
                "hotelId": "hotel_budget_01",
                "name": "如家精选酒店 (上海虹桥枢纽店)",
                "star": 3,
                "distanceKm": 1.8,
                "price": 239.0
            },
            {
                "hotelId": "hotel_luxury_02",
                "name": "上海虹桥康得思酒店 (航站楼直连)",
                "star": 5,
                "distanceKm": 0.5,
                "price": 899.0
            }
        ],
        "attractions": [
            {
                "ticketId": "ticket_disney_01",
                "name": "上海迪士尼度假区门票 (次日特惠票)",
                "price": 435.0,
                "tag": "提前订立减 50 元"
            },
            {
                "ticketId": "ticket_bund_cruise_02",
                "name": "黄浦江夜游游船票 (含接驳)",
                "price": 128.0,
                "tag": "外滩夜景优选"
            }
        ]
    }


@app.post("/mock/tools/flights")
def mock_flight_search(payload: Dict[str, Any]):
    origin = payload.get("origin", "北京")
    dest = payload.get("destination", "上海")
    return [
        {"flightNo": "CA1831", "airline": "国航", "origin": origin, "destination": dest, "depTime": "08:30", "arrTime": "10:45", "price": 980, "seatClass": "经济舱", "luggage": "含免费托运行李20KG"},
        {"flightNo": "MU5102", "airline": "东航", "origin": origin, "destination": dest, "depTime": "11:00", "arrTime": "13:20", "price": 850, "seatClass": "经济舱", "luggage": "含免费托运行李20KG"},
        {"flightNo": "CZ3901", "airline": "南航", "origin": origin, "destination": dest, "depTime": "15:00", "arrTime": "17:15", "price": 1120, "seatClass": "超经舱", "luggage": "含免费托运行李30KG"},
    ]


@app.post("/mock/tools/calc")
def mock_calculator(payload: Dict[str, Any]):
    expr = payload.get("expression", "0")
    try:
        # 安全计算基础数学公式
        allowed = set("0123456789+-*/(). ")
        if all(c in allowed for c in expr):
            res = eval(expr)
            return {"result": res, "expression": expr}
        return {"result": 0, "error": "表达式包含不支持字符"}
    except Exception as e:
        return {"result": 0, "error": str(e)}


# ==========================================
# 3. 前端管理控制台配置操作 API
# ==========================================

@app.get("/admin/configs")
def get_all_configs():
    return config_store.get_all()


@app.post("/admin/reset")
def reset_configs():
    return config_store.reset()


class SaveItemRequest(BaseModel):
    category: str
    item: Dict[str, Any]
    key_field: Optional[str] = None


@app.post("/admin/configs")
def save_config_item(req: SaveItemRequest):
    key_field_map = {
        "strategies": "strategyId",
        "agents": "name",
        "tools": "name",
        "models": "code",
        "dict": "key",
    }
    field = req.key_field or key_field_map.get(req.category, "name")
    saved = config_store.save_item(req.category, req.item, key_field=field)
    return {"success": True, "data": saved}


@app.delete("/admin/configs/{category}/{key_val}")
def delete_config_item(category: str, key_val: str):
    key_field_map = {
        "strategies": "strategyId",
        "agents": "name",
        "tools": "name",
        "models": "code",
        "dict": "key",
    }
    field = key_field_map.get(category, "name")
    deleted = config_store.delete_item(category, key_val, key_field=field)
    return {"success": deleted}


# ==========================================
# 4. Java OrchestrAgent 控制器联调转发代理
# ==========================================

@app.post("/proxy/invoke")
async def proxy_invoke(payload: Dict[str, Any]):
    url = f"{JAVA_BACKEND_URL}/api/agent/invoke"
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(url, json=payload)
            return resp.json()
    except httpx.ConnectError:
        raise HTTPException(status_code=502, detail=f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})，请确认 Java 应用已启动在 8080 端口")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/proxy/testInvoke")
async def proxy_test_invoke(payload: Dict[str, Any]):
    url = f"{JAVA_BACKEND_URL}/api/agent/testInvoke"
    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            resp = await client.post(url, json=payload)
            return resp.json()
    except httpx.ConnectError:
        raise HTTPException(status_code=502, detail=f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})，请确认 Java 应用已启动在 8080 端口")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/proxy/chat")
async def proxy_chat(request: Request):
    """
    流式代理 Java 的 /api/agent/chat (SSE)
    """
    url = f"{JAVA_BACKEND_URL}/api/agent/chat"
    body = await request.json()

    async def sse_generator():
        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                async with client.stream("POST", url, json=body, headers={"Accept": "text/event-stream"}) as response:
                    if response.status_code != 200:
                        yield f"event: error\ndata: Java 服务返回错误码 {response.status_code}\n\n"
                        return
                    async for line in response.aiter_lines():
                        if line:
                            yield f"{line}\n"
                        else:
                            yield "\n"
        except httpx.ConnectError:
            yield f"event: error\ndata: 无法连接到 Java 服务 ({JAVA_BACKEND_URL})\n\n"
        except Exception as e:
            yield f"event: error\ndata: 代理流异常: {str(e)}\n\n"

    return StreamingResponse(sse_generator(), media_type="text/event-stream")


@app.get("/proxy/java/overview")
async def proxy_java_overview():
    url = f"{JAVA_BACKEND_URL}/api/agent/config/overview"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url)
            return resp.json()
    except httpx.ConnectError:
        return {"success": False, "code": "CONNECT_ERROR", "message": f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})"}
    except Exception as e:
        return {"success": False, "code": "ERROR", "message": str(e)}


@app.get("/proxy/java/all")
async def proxy_java_all():
    url = f"{JAVA_BACKEND_URL}/api/agent/config/all"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url)
            return resp.json()
    except httpx.ConnectError:
        return {"success": False, "code": "CONNECT_ERROR", "message": f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})"}
    except Exception as e:
        return {"success": False, "code": "ERROR", "message": str(e)}


@app.post("/proxy/java/reload")
async def proxy_java_reload(repo: Optional[str] = None):
    url = f"{JAVA_BACKEND_URL}/api/agent/config/reload"
    params = {}
    if repo:
        params["repoName"] = repo
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(url, params=params)
            return resp.json()
    except httpx.ConnectError:
        return {"success": False, "code": "CONNECT_ERROR", "message": f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})"}
    except Exception as e:
        return {"success": False, "code": "ERROR", "message": str(e)}


@app.get("/proxy/logs/stream")
async def proxy_logs_stream(tail: int = 100, filter: Optional[str] = None):
    """
    流式代理 Java 侧 /api/agent/logs/stream (SSE)
    """
    url = f"{JAVA_BACKEND_URL}/api/agent/logs/stream?tail={tail}"
    if filter:
        url += f"&filter={filter}"

    async def sse_generator():
        try:
            async with httpx.AsyncClient(timeout=None) as client:
                async with client.stream("GET", url, headers={"Accept": "text/event-stream"}) as response:
                    if response.status_code != 200:
                        yield f"event: error\ndata: Java 日志服务响应异常 ({response.status_code})\n\n"
                        return
                    async for line in response.aiter_lines():
                        if line:
                            yield f"{line}\n"
                        else:
                            yield "\n"
        except httpx.ConnectError:
            yield f"event: error\ndata: 无法连接到 Java 服务 ({JAVA_BACKEND_URL})，请确认 Java 应用已启动在 8080 端口\n\n"
        except Exception as e:
            yield f"event: error\ndata: 日志代理流异常: {str(e)}\n\n"

    return StreamingResponse(sse_generator(), media_type="text/event-stream")


@app.post("/proxy/logs/clear")
async def proxy_logs_clear():
    url = f"{JAVA_BACKEND_URL}/api/agent/logs/clear"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(url)
            return resp.json()
    except httpx.ConnectError:
        return {"success": False, "code": "CONNECT_ERROR", "message": f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})"}
    except Exception as e:
        return {"success": False, "code": "ERROR", "message": str(e)}



# ==========================================
# 5. 挂载静态 Web 交互页面
# ==========================================

static_dir = os.path.join(os.path.dirname(__file__), "static")
os.makedirs(static_dir, exist_ok=True)
app.mount("/", StaticFiles(directory=static_dir, html=True), name="static")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host=SERVER_HOST, port=SERVER_PORT, reload=True)
