import copy
import json
import os
import uuid
from typing import Dict, List, Any, Optional

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
MOCK_RULES_FILE = os.path.join(DATA_DIR, "mock_rules.json")

DEFAULT_MOCK_RULES = [
    # ==================== query_weather_tool ====================
    {
        "id": "rule_weather_shanghai",
        "toolCode": "query_weather_tool",
        "description": "上海落地-夜间暴雨场景 (促发夜间特惠专车与就近酒店推荐)",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "city": "上海"
        },
        "responseStatus": 200,
        "responseData": {
            "city": "上海",
            "airportCode": "SHA",
            "weather": "RAINY",
            "temperature": 18.5,
            "isNight": True,
            "description": "夜间阴有中雨，气温 18.5°C，东风 3 级，路面湿滑，建议提前规划接驳交通"
        }
    },
    {
        "id": "rule_weather_beijing",
        "toolCode": "query_weather_tool",
        "description": "北京落地-白天晴朗场景 (促发舒适商务车与故宫/景区出行)",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "city": "北京"
        },
        "responseStatus": 200,
        "responseData": {
            "city": "北京",
            "airportCode": "PEK",
            "weather": "SUNNY",
            "temperature": 22.0,
            "isNight": False,
            "description": "白天晴朗少云，气温 22.0°C，微风 2 级，能见度优，适宜出行"
        }
    },
    {
        "id": "rule_weather_guangzhou",
        "toolCode": "query_weather_tool",
        "description": "广州落地-白天多云场景",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "city": "广州"
        },
        "responseStatus": 200,
        "responseData": {
            "city": "广州",
            "airportCode": "CAN",
            "weather": "CLOUDY",
            "temperature": 26.5,
            "isNight": False,
            "description": "白天多云转阴，气温 26.5°C，微风，体感舒适"
        }
    },
    {
        "id": "rule_weather_default",
        "toolCode": "query_weather_tool",
        "description": "通用天气兜底规则",
        "enabled": True,
        "priority": 1,
        "matchParams": {},
        "responseStatus": 200,
        "responseData": {
            "city": "上海",
            "airportCode": "SHA",
            "weather": "RAINY",
            "temperature": 19.0,
            "isNight": True,
            "description": "落地阴雨天气，气温舒适，路面湿滑"
        }
    },

    # ==================== query_user_profile_tool ====================
    {
        "id": "rule_profile_u8801",
        "toolCode": "query_user_profile_tool",
        "description": "旅客 U8801 - 价格敏感高性价比画像 (偏好经济特惠接送与经济型酒店)",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "userId": "U8801"
        },
        "responseStatus": 200,
        "responseData": {
            "userId": "U8801",
            "preferenceType": "PRICE_FIRST",
            "historicalSpendingLevel": "LOW",
            "frequentHotelStar": 3,
            "tags": ["价格敏感", "高性价比偏好", "偏好拼车/特惠接送"]
        }
    },
    {
        "id": "rule_profile_vip001",
        "toolCode": "query_user_profile_tool",
        "description": "旅客 VIP001 - 高净值商务尊享画像 (偏好豪华专车与五星高端酒店)",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "userId": "VIP001"
        },
        "responseStatus": 200,
        "responseData": {
            "userId": "VIP001",
            "preferenceType": "EXPERIENCE_FIRST",
            "historicalSpendingLevel": "HIGH",
            "frequentHotelStar": 5,
            "tags": ["高净值商旅", "商务品质优先", "豪华专车与五星酒店偏好"]
        }
    },
    {
        "id": "rule_profile_u1002",
        "toolCode": "query_user_profile_tool",
        "description": "旅客 U1002 - 家庭亲子出游画像",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "userId": "U1002"
        },
        "responseStatus": 200,
        "responseData": {
            "userId": "U1002",
            "preferenceType": "EXPERIENCE_FIRST",
            "historicalSpendingLevel": "MEDIUM",
            "frequentHotelStar": 4,
            "tags": ["家庭亲子", "偏好景区套票", "舒适商务接驳"]
        }
    },
    {
        "id": "rule_profile_default",
        "toolCode": "query_user_profile_tool",
        "description": "通用用户画像兜底规则",
        "enabled": True,
        "priority": 1,
        "matchParams": {},
        "responseStatus": 200,
        "responseData": {
            "userId": "DEFAULT_USER",
            "preferenceType": "PRICE_FIRST",
            "historicalSpendingLevel": "LOW",
            "frequentHotelStar": 3,
            "tags": ["标准商旅", "性价比偏好"]
        }
    },

    # ==================== query_arrival_services_tool ====================
    {
        "id": "rule_services_sha",
        "toolCode": "query_arrival_services_tool",
        "description": "上海虹桥机场 (SHA) 周边接驳、酒店与门票供给",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "airportCode": "SHA"
        },
        "responseStatus": 200,
        "responseData": {
            "airportCode": "SHA",
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
    },
    {
        "id": "rule_services_pek",
        "toolCode": "query_arrival_services_tool",
        "description": "北京首都/大兴机场 (PEK) 周边接驳、酒店与门票供给",
        "enabled": True,
        "priority": 10,
        "matchParams": {
            "airportCode": "PEK"
        },
        "responseStatus": 200,
        "responseData": {
            "airportCode": "PEK",
            "pickupServices": [
                {
                    "serviceId": "pickup_pek_01",
                    "carType": "COMFORT",
                    "title": "首都机场品质商务专车",
                    "price": 128.0,
                    "features": "航站楼接驳、免费矿泉水、专职司机"
                },
                {
                    "serviceId": "pickup_pek_02",
                    "carType": "LUXURY",
                    "title": "首都机场豪华行政专车",
                    "price": 288.0,
                    "features": "奔驰E级/宝马5系同级、头等贵宾服务"
                }
            ],
            "hotels": [
                {
                    "hotelId": "hotel_pek_01",
                    "name": "北京国贸大酒店",
                    "star": 5,
                    "distanceKm": 22.0,
                    "price": 1280.0
                },
                {
                    "hotelId": "hotel_pek_02",
                    "name": "北京临空皇冠假日酒店",
                    "star": 4,
                    "distanceKm": 3.5,
                    "price": 480.0
                }
            ],
            "attractions": [
                {
                    "ticketId": "ticket_gugong_01",
                    "name": "故宫博物院特惠门票 (含珍宝馆)",
                    "price": 60.0,
                    "tag": "北京必游经典"
                },
                {
                    "ticketId": "ticket_universal_02",
                    "name": "北京环球度假区指定单日门票",
                    "price": 418.0,
                    "tag": "畅玩大片世界"
                }
            ]
        }
    },
    {
        "id": "rule_services_default",
        "toolCode": "query_arrival_services_tool",
        "description": "到达地周边服务通用兜底",
        "enabled": True,
        "priority": 1,
        "matchParams": {},
        "responseStatus": 200,
        "responseData": {
            "airportCode": "DEFAULT",
            "pickupServices": [
                {
                    "serviceId": "pickup_default_01",
                    "carType": "ECONOMY",
                    "title": "机场特惠拼车/快车",
                    "price": 50.0,
                    "features": "经济实惠、随叫随到"
                }
            ],
            "hotels": [
                {
                    "hotelId": "hotel_default_01",
                    "name": "机场连锁商务酒店",
                    "star": 3,
                    "distanceKm": 2.0,
                    "price": 200.0
                }
            ],
            "attractions": []
        }
    }
]


class MockRuleStore:
    def __init__(self):
        os.makedirs(DATA_DIR, exist_ok=True)
        self.rules: List[Dict[str, Any]] = self._load()

    def _load(self) -> List[Dict[str, Any]]:
        if os.path.exists(MOCK_RULES_FILE):
            try:
                with open(MOCK_RULES_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    if isinstance(data, list):
                        print(f"[MockRuleStore] 成功从本地加载 {len(data)} 条 Mock 规则")
                        return data
            except Exception as e:
                print(f"[MockRuleStore] 读取 {MOCK_RULES_FILE} 失败，使用默认规则: {e}")

        defaults = copy.deepcopy(DEFAULT_MOCK_RULES)
        self._save_to_file(defaults)
        return defaults

    def _save_to_file(self, rules: List[Dict[str, Any]]):
        try:
            with open(MOCK_RULES_FILE, "w", encoding="utf-8") as f:
                json.dump(rules, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"[MockRuleStore] 写入 {MOCK_RULES_FILE} 异常: {e}")

    def save(self):
        self._save_to_file(self.rules)

    def get_all(self, tool_code: Optional[str] = None) -> List[Dict[str, Any]]:
        if tool_code:
            return [r for r in self.rules if r.get("toolCode") == tool_code]
        return self.rules

    def get_by_id(self, rule_id: str) -> Optional[Dict[str, Any]]:
        for r in self.rules:
            if r.get("id") == rule_id:
                return r
        return None

    def save_rule(self, rule: Dict[str, Any]) -> Dict[str, Any]:
        rule_id = rule.get("id")
        if not rule_id:
            rule_id = f"rule_{uuid.uuid4().hex[:8]}"
            rule["id"] = rule_id

        rule.setdefault("enabled", True)
        rule.setdefault("priority", 10)
        rule.setdefault("responseStatus", 200)
        rule.setdefault("matchParams", {})

        for i, existing in enumerate(self.rules):
            if existing.get("id") == rule_id:
                self.rules[i] = rule
                self.save()
                return rule

        self.rules.append(rule)
        self.save()
        return rule

    def delete_rule(self, rule_id: str) -> bool:
        before_len = len(self.rules)
        self.rules = [r for r in self.rules if r.get("id") != rule_id]
        if len(self.rules) != before_len:
            self.save()
            return True
        return False

    def reset(self) -> List[Dict[str, Any]]:
        self.rules = copy.deepcopy(DEFAULT_MOCK_RULES)
        self.save()
        return self.rules

    def match_rule(self, tool_code: str, payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """
        根据 toolCode 与传入 payload 查找最匹配的 Mock 规则
        规则匹配逻辑：
        1. 必须 toolCode 一致且 enabled == True
        2. 按 priority 降序排序
        3. matchParams 中的每个键值对必须与 payload 中的值匹配（若 matchParams 为空，则作为通用兜底规则）
        """
        candidate_rules = [
            r for r in self.rules
            if r.get("toolCode") == tool_code and r.get("enabled", True)
        ]
        # 按 priority 降序
        candidate_rules.sort(key=lambda x: x.get("priority", 0), reverse=True)

        for rule in candidate_rules:
            match_params = rule.get("matchParams", {})
            if not match_params:
                # 空条件为兜底规则，记录后继续找是否有更精确匹配
                continue

            # 检查入参匹配
            is_match = True
            for k, v in match_params.items():
                if k not in payload or str(payload[k]).strip() != str(v).strip():
                    is_match = False
                    break
            if is_match:
                return rule

        # 若无精准匹配，返回兜底规则（matchParams 为空的规则）
        for rule in candidate_rules:
            if not rule.get("matchParams"):
                return rule

        return None


mock_rule_store = MockRuleStore()
