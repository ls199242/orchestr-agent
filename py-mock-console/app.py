import os
import json
import time
import uuid
import httpx
from fastapi import FastAPI, Request, HTTPException, Response, Body
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


from mock_store import mock_rule_store

# ==========================================
# 2. 仿真业务工具 Mock 端点 (支持基于不同入参动态匹配 Mock 规则)
# ==========================================

@app.post("/mock/tools/weather")
def mock_weather_tool(payload: Optional[Dict[str, Any]] = Body(default=None)):
    payload = payload or {}
    matched = mock_rule_store.match_rule("query_weather_tool", payload)
    if matched:
        return JSONResponse(status_code=matched.get("responseStatus", 200), content=matched.get("responseData", {}))
    city = payload.get("city") or payload.get("arrivalCity") or "上海"
    airport_code = payload.get("airportCode") or payload.get("arrivalAirport") or "SHA"
    return {
        "city": city,
        "airportCode": airport_code,
        "weather": "RAINY",
        "temperature": 18.5,
        "isNight": True,
        "description": f"{city}落地时刻阴有中雨，气温 18.5°C，东风 3 级，路面湿滑，建议提前规划接驳交通"
    }


@app.post("/mock/tools/user-profile")
def mock_user_profile_tool(payload: Optional[Dict[str, Any]] = Body(default=None)):
    payload = payload or {}
    matched = mock_rule_store.match_rule("query_user_profile_tool", payload)
    if matched:
        return JSONResponse(status_code=matched.get("responseStatus", 200), content=matched.get("responseData", {}))
    user_id = payload.get("userId") or payload.get("user_id") or "U8801"
    return {
        "userId": user_id,
        "preferenceType": "PRICE_FIRST",
        "preferenceTag": "PRICE_FIRST",
        "historicalSpendingLevel": "LOW",
        "consumptionLevel": "LOW",
        "priceSensitivity": "HIGH",
        "frequentHotelStar": 3,
        "tags": ["价格敏感", "高性价比偏好", "偏好拼车/特惠接送"]
    }


@app.post("/mock/tools/arrival-services")
def mock_arrival_services_tool(payload: Optional[Dict[str, Any]] = Body(default=None)):
    payload = payload or {}
    matched = mock_rule_store.match_rule("query_arrival_services_tool", payload)
    if matched:
        return JSONResponse(status_code=matched.get("responseStatus", 200), content=matched.get("responseData", {}))
    airport_code = payload.get("airportCode") or payload.get("arrivalAirport") or "SHA"
    pickup_services = [
        {
            "serviceId": "pickup_car_economy_01",
            "carType": "ECONOMY",
            "title": f"{airport_code} 机场直达专车 · 经济特惠型",
            "price": 58.0,
            "features": "即走免等、雨夜特惠、比现场打车便宜 25%"
        }
    ]
    hotels = [
        {
            "hotelId": "hotel_budget_01",
            "name": f"如家精选酒店 ({airport_code}枢纽店)",
            "star": 3,
            "distanceKm": 1.8,
            "price": 239.0
        }
    ]
    attractions = []
    return {
        "airportCode": airport_code,
        "pickupServices": pickup_services,
        "hotels": hotels,
        "attractions": attractions,
        "services": pickup_services + hotels
    }


@app.post("/mock/tools/user-info")
def mock_user_info_tool(payload: Optional[Dict[str, Any]] = Body(default=None)):
    payload = payload or {}
    matched = mock_rule_store.match_rule("query_user_info_tool", payload)
    if matched:
        return JSONResponse(status_code=matched.get("responseStatus", 200), content=matched.get("responseData", {}))
    user_id = payload.get("userId", "U1001")
    return {
        "userId": user_id,
        "userName": "张先生",
        "memberTier": "GOLD",
        "homeCity": "北京",
        "preferredAirlines": ["国航", "东航"],
        "preferredCabin": "PREMIUM_ECONOMY",
        "priceSensitivity": "LOW",
        "tags": ["商务差旅", "金卡会员", "偏好早班机", "偏好宽体客机"]
    }


@app.post("/mock/tools/user-orders")
def mock_user_orders_tool(payload: Optional[Dict[str, Any]] = Body(default=None)):
    payload = payload or {}
    matched = mock_rule_store.match_rule("query_user_orders_tool", payload)
    if matched:
        return JSONResponse(status_code=matched.get("responseStatus", 200), content=matched.get("responseData", {}))
    user_id = payload.get("userId", "U1001")
    return {
        "userId": user_id,
        "totalOrders": 12,
        "frequentDepTimeSlot": "早间 (07:30-09:30)",
        "frequentAirlines": ["国航", "东航"],
        "orders": [
            {
                "orderId": "ORD2026101501",
                "flightNo": "CA1501",
                "airline": "国航",
                "depAirport": "PEK",
                "arrAirport": "SHA",
                "depDate": "2026-10-15",
                "depTime": "08:00",
                "cabinClass": "超经舱",
                "ticketPrice": 1250.0
            },
            {
                "orderId": "ORD2026092002",
                "flightNo": "MU5108",
                "airline": "东航",
                "depAirport": "PEK",
                "arrAirport": "SHA",
                "depDate": "2026-09-20",
                "depTime": "08:30",
                "cabinClass": "经济舱",
                "ticketPrice": 980.0
            }
        ]
    }


@app.post("/mock/tools/flights")
def mock_flight_search(payload: Optional[Dict[str, Any]] = Body(default=None)):
    payload = payload or {}
    matched = mock_rule_store.match_rule("query_flight_schedule_tool", payload) or mock_rule_store.match_rule("query_flights_tool", payload)
    if matched:
        return JSONResponse(status_code=matched.get("responseStatus", 200), content=matched.get("responseData", {}))
    dep_city = payload.get("departureCity") or payload.get("origin", "北京")
    arr_city = payload.get("arrivalCity") or payload.get("destination", "上海")
    dep_date = payload.get("departureDate", "2026-11-10")
    return {
        "departureCity": dep_city,
        "arrivalCity": arr_city,
        "departureDate": dep_date,
        "flights": [
            {
                "flightNo": "CA1501",
                "airline": "国航",
                "depAirport": "PEK",
                "arrAirport": "SHA",
                "depDate": dep_date,
                "depTime": "08:00",
                "arrTime": "10:15",
                "price": 1080.0,
                "seatClass": "超经舱",
                "punctualityRate": "92%",
                "isNearbyDate": False,
                "isNearbyAirport": False,
                "aircraftType": "宽体机 B777"
            },
            {
                "flightNo": "MU5108",
                "airline": "东航",
                "depAirport": "PEK",
                "arrAirport": "PVG",
                "depDate": dep_date,
                "depTime": "13:00",
                "arrTime": "15:15",
                "price": 820.0,
                "seatClass": "经济舱",
                "punctualityRate": "88%",
                "isNearbyDate": False,
                "isNearbyAirport": False,
                "aircraftType": "窄体机 A320"
            },
            {
                "flightNo": "CZ3101",
                "airline": "南航",
                "depAirport": "PKX",
                "arrAirport": "SHA",
                "depDate": dep_date,
                "depTime": "08:30",
                "arrTime": "10:45",
                "price": 750.0,
                "seatClass": "经济舱",
                "punctualityRate": "95%",
                "isNearbyDate": False,
                "isNearbyAirport": True,
                "aircraftType": "宽体机 A350",
                "remark": "大兴机场起飞，草桥快线空轨联运，立省 25%"
            },
            {
                "flightNo": "HU7605",
                "airline": "海航",
                "depAirport": "PEK",
                "arrAirport": "SHA",
                "depDate": "2026-11-11",
                "depTime": "09:00",
                "arrTime": "11:15",
                "price": 560.0,
                "seatClass": "经济舱",
                "punctualityRate": "90%",
                "isNearbyDate": True,
                "isNearbyAirport": False,
                "aircraftType": "窄体机 B737",
                "remark": "临近次日航班特惠，较基准日立省 40%"
            }
        ]
    }


@app.post("/mock/tools/calc")
def mock_calculator(payload: Optional[Dict[str, Any]] = Body(default=None)):
    payload = payload or {}
    matched = mock_rule_store.match_rule("calc_tool", payload)
    if matched:
        return JSONResponse(status_code=matched.get("responseStatus", 200), content=matched.get("responseData", {}))
    expr = payload.get("expression", "0")
    try:
        allowed = set("0123456789+-*/(). ")
        if all(c in allowed for c in expr):
            res = eval(expr)
            return {"result": res, "expression": expr}
        return {"result": 0, "error": "表达式包含不支持字符"}
    except Exception as e:
        return {"result": 0, "error": str(e)}


# ==========================================
# 2.0 仿真 OpenAI / DeepSeek 兼容大模型端点 (支持完整协同流离线联调)
# ==========================================

@app.post("/mock/llm/v1/chat/completions")
async def mock_llm_chat_completions(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}

    stream = body.get("stream", False)
    messages = body.get("messages", [])
    model = body.get("model", "deepseek-flash")

    all_text = " ".join([m.get("content", "") for m in messages if isinstance(m, dict) and "content" in m])

    # 1. 任务规划智能体 (PlannerAgent)
    if "PlannerAgent" in all_text or "无环依赖图" in all_text or "DAG" in all_text or "规划大师" in all_text:
        content = json.dumps({
            "steps": [
                {
                    "id": "s1",
                    "agent": "query_weather_tool",
                    "goal": "获取到达地天气与昼夜信息",
                    "inputs": {
                        "city": "$global.arrivalCity",
                        "airportCode": "$global.arrivalAirport"
                    },
                    "dependsOn": [],
                    "outputKeys": ["weather", "temperature"],
                    "maxRetries": 1,
                    "timeoutMs": 15000,
                    "onFailure": "ABORT"
                },
                {
                    "id": "s2",
                    "agent": "query_user_profile_tool",
                    "goal": "获取旅客消费偏好画像",
                    "inputs": {
                        "userId": "$global.userId"
                    },
                    "dependsOn": [],
                    "outputKeys": ["preferenceType"],
                    "maxRetries": 1,
                    "timeoutMs": 15000,
                    "onFailure": "ABORT"
                },
                {
                    "id": "s3",
                    "agent": "query_arrival_services_tool",
                    "goal": "检索落地机场周边可用服务",
                    "inputs": {
                        "airportCode": "$global.arrivalAirport"
                    },
                    "dependsOn": [],
                    "outputKeys": ["pickupServices", "hotels"],
                    "maxRetries": 1,
                    "timeoutMs": 15000,
                    "onFailure": "ABORT"
                },
                {
                    "id": "s4",
                    "agent": "arrival_recommend_expert",
                    "goal": "综合天气、画像与服务列表生成个性化推荐方案",
                    "inputs": {
                        "weather": "$s1.output",
                        "profile": "$s2.output",
                        "services": "$s3.output"
                    },
                    "dependsOn": ["s1", "s2", "s3"],
                    "outputKeys": [],
                    "maxRetries": 1,
                    "timeoutMs": 20000,
                    "onFailure": "ABORT"
                }
            ]
        }, ensure_ascii=False)
    # 2. 会话协调智能体 (CoordinatorAgent)
    elif "CoordinatorAgent" in all_text or "多轮对话意图识别" in all_text:
        content = json.dumps({
            "action": "EXECUTE",
            "reply": "已为您识别到航班落地交叉营销策略，正在拉起协同流处理...",
            "strategyCode": "flight_arrival_cross_sell",
            "params": {
                "flightNo": "CA1234",
                "arrivalCity": "上海",
                "arrivalAirport": "SHA",
                "userId": "U8801"
            }
        }, ensure_ascii=False)
    # 3. 验收审计智能体 (EvaluatorAgent)
    elif "EvaluatorAgent" in all_text or "质检验收专家" in all_text:
        content = json.dumps({
            "pass": True,
            "score": 96,
            "critique": "各协同节点产出与参数绑定完整，满足战略目标与天气画像约束",
            "suggestedRemedy": "",
            "blamedStepIds": []
        }, ensure_ascii=False)
    # 4. 汇报交付智能体 (ReporterAgent)
    elif "ReporterAgent" in all_text or "成果汇编与交付专家" in all_text or "Reporter" in all_text:
        content = json.dumps({
            "strategyCode": "flight_arrival_cross_sell",
            "userId": "U8801",
            "flightNo": "CA1234",
            "contextSummary": {
                "weatherDesc": "阴有中雨 18.5°C",
                "timeOfDay": "夜间",
                "userCategory": "价格敏感型"
            },
            "recommendations": [
                {
                    "serviceType": "PICKUP",
                    "itemId": "pickup_car_economy_01",
                    "title": "SHA 机场直达专车 · 经济特惠型",
                    "tag": "雨夜特惠",
                    "price": 58.0,
                    "originalPrice": 78.0,
                    "recommendedReason": "落地时刻有雨且处于夜间，专车即走免等，价格实惠"
                },
                {
                    "serviceType": "HOTEL",
                    "itemId": "hotel_budget_01",
                    "title": "如家精选酒店 (SHA枢纽店)",
                    "tag": "高性价比",
                    "price": 239.0,
                    "originalPrice": 299.0,
                    "recommendedReason": "紧邻机场1.8公里，符合您常住三星级且价格优先的偏好"
                }
            ]
        }, ensure_ascii=False)
    # 5. 简单计算测试 (1+1)
    elif "1+1" in all_text:
        content = "2"
    # 6. 专精执行专家 (如 arrival_recommend_expert)
    else:
        content = "【落地服务推荐专家决策】经分析到达地阴雨天气与旅客消费偏好（价格敏感型），推荐高性价比雨夜接送机专车以及就近经济型精选酒店。"

    if stream:
        async def event_generator():
            chunk_size = 30
            for i in range(0, len(content), chunk_size):
                chunk = content[i:i+chunk_size]
                chunk_obj = {
                    "id": f"chatcmpl-mock-{uuid.uuid4().hex[:8]}",
                    "object": "chat.completion.chunk",
                    "created": int(time.time()),
                    "model": model,
                    "choices": [{
                        "index": 0,
                        "delta": {"content": chunk},
                        "finish_reason": None
                    }]
                }
                yield f"data: {json.dumps(chunk_obj, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"
        return StreamingResponse(event_generator(), media_type="text/event-stream")
    else:
        return {
            "id": f"chatcmpl-mock-{uuid.uuid4().hex[:8]}",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": model,
            "choices": [{
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": content
                },
                "finish_reason": "stop"
            }]
        }


# ==========================================
# 2.1 Mock 规则中心管理 API
# ==========================================

@app.get("/admin/mocks")
def get_mock_rules(toolCode: Optional[str] = None):
    return {"success": True, "data": mock_rule_store.get_all(tool_code=toolCode)}


@app.post("/admin/mocks")
def save_mock_rule(rule: Dict[str, Any]):
    saved = mock_rule_store.save_rule(rule)
    return {"success": True, "data": saved}


@app.delete("/admin/mocks/{rule_id}")
def delete_mock_rule(rule_id: str):
    deleted = mock_rule_store.delete_rule(rule_id)
    return {"success": deleted}


@app.post("/admin/mocks/reset")
def reset_mock_rules():
    rules = mock_rule_store.reset()
    return {"success": True, "data": rules}


class TestMockRequest(BaseModel):
    toolCode: str
    payload: Dict[str, Any]


# Fallback response provider for testing workbench
def get_fallback_tool_response(tool_code: str, payload: Dict[str, Any]) -> Any:
    payload = payload or {}
    if tool_code == "query_weather_tool":
        city = payload.get("city") or payload.get("arrivalCity") or "上海"
        airport_code = payload.get("airportCode") or payload.get("arrivalAirport") or "SHA"
        return {
            "city": city,
            "airportCode": airport_code,
            "weather": "RAINY",
            "temperature": 18.5,
            "isNight": True,
            "description": f"{city}落地时刻阴有中雨，气温 18.5°C，东风 3 级，路面湿滑，建议提前规划接驳交通"
        }
    elif tool_code == "query_user_profile_tool":
        user_id = payload.get("userId") or payload.get("user_id") or "U8801"
        return {
            "userId": user_id,
            "preferenceType": "PRICE_FIRST",
            "preferenceTag": "PRICE_FIRST",
            "historicalSpendingLevel": "LOW",
            "consumptionLevel": "LOW",
            "priceSensitivity": "HIGH",
            "frequentHotelStar": 3,
            "tags": ["价格敏感", "高性价比偏好", "偏好拼车/特惠接送"]
        }
    elif tool_code == "query_arrival_services_tool":
        airport_code = payload.get("airportCode") or payload.get("arrivalAirport") or "SHA"
        pickup_services = [{
            "serviceId": "pickup_car_economy_01",
            "carType": "ECONOMY",
            "title": f"{airport_code} 机场直达专车 · 经济特惠型",
            "price": 58.0,
            "features": "即走免等、雨夜特惠、比现场打车便宜 25%"
        }]
        hotels = [{
            "hotelId": "hotel_budget_01",
            "name": f"如家精选酒店 ({airport_code}枢纽店)",
            "star": 3,
            "distanceKm": 1.8,
            "price": 239.0
        }]
        return {
            "airportCode": airport_code,
            "pickupServices": pickup_services,
            "hotels": hotels,
            "attractions": [],
            "services": pickup_services + hotels
        }
    elif tool_code == "query_flights_tool":
        origin = payload.get("origin", "北京")
        dest = payload.get("destination", "上海")
        return [
            {"flightNo": "CA1831", "airline": "国航", "origin": origin, "destination": dest, "depTime": "08:30", "arrTime": "10:45", "price": 980, "seatClass": "经济舱", "luggage": "含免费托运行李20KG"},
            {"flightNo": "MU5102", "airline": "东航", "origin": origin, "destination": dest, "depTime": "11:00", "arrTime": "13:20", "price": 850, "seatClass": "经济舱", "luggage": "含免费托运行李20KG"},
        ]
    elif tool_code == "calc_tool":
        expr = payload.get("expression", "0")
        return {"result": 0, "expression": expr, "fallback": True}
    return {
        "status": "fallback",
        "toolCode": tool_code,
        "message": "未命中自定义规则，将执行系统默认兜底处理",
        "echoPayload": payload
    }

@app.post("/admin/mocks/test")
def test_mock_rule(req: TestMockRequest):
    matched = mock_rule_store.match_rule(req.toolCode, req.payload)
    if matched:
        return {
            "matched": True,
            "ruleId": matched.get("id"),
            "description": matched.get("description"),
            "responseStatus": matched.get("responseStatus", 200),
            "responseData": matched.get("responseData"),
        }
    fallback_data = get_fallback_tool_response(req.toolCode, req.payload)
    return {
        "matched": False,
        "ruleId": None,
        "description": "未命中任何专属规则，已按工具底层默认逻辑动态返回兜底数据",
        "responseStatus": 200,
        "responseData": fallback_data,
    }


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


@app.get("/proxy/flow/{flow_id}")
async def proxy_get_flow(flow_id: str):
    url = f"{JAVA_BACKEND_URL}/api/agent/flow/{flow_id}"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url)
            return resp.json()
    except httpx.ConnectError:
        raise HTTPException(status_code=502, detail=f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})，请确认 Java 应用已启动在 8080 端口")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/proxy/flow/{flow_id}/logs")
async def proxy_get_flow_logs(flow_id: str):
    url = f"{JAVA_BACKEND_URL}/api/agent/flow/{flow_id}/logs"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url)
            return resp.json()
    except httpx.ConnectError:
        raise HTTPException(status_code=502, detail=f"无法连接到 Java 服务 ({JAVA_BACKEND_URL})，请确认 Java 应用已启动在 8080 端口")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/proxy/stop/{flow_id}")
@app.post("/proxy/cancel/{flow_id}")
async def proxy_stop_flow(flow_id: str):
    url = f"{JAVA_BACKEND_URL}/api/agent/stop/{flow_id}"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(url)
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
