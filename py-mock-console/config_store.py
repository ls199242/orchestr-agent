import copy
import importlib
import json
import os
from typing import Dict, List, Any, Optional

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
DATA_FILE = os.path.join(DATA_DIR, "configs.json")


def load_config_module():
    """动态安全加载 config.py（优先）或 config.py.example（保底）"""
    try:
        import config
        return config
    except ImportError:
        try:
            import config_example
            return config_example
        except ImportError:
            return None


def get_default_configs() -> Dict[str, Any]:
    """从 config.py 或 fallback 加载默认配置字典"""
    cfg = load_config_module()
    if cfg and hasattr(cfg, "DEFAULT_CONFIGS"):
        # 如果模块有更新，允许重新读取最新属性
        return copy.deepcopy(cfg.DEFAULT_CONFIGS)
    return {
        "strategies": [],
        "agents": [],
        "tools": [],
        "models": [],
        "dict": [],
    }


def should_overwrite_on_startup() -> bool:
    cfg = load_config_module()
    if cfg and hasattr(cfg, "OVERWRITE_DATA_WITH_CONFIG"):
        return bool(cfg.OVERWRITE_DATA_WITH_CONFIG)
    return False


class ConfigStore:
    def __init__(self):
        os.makedirs(DATA_DIR, exist_ok=True)
        self._last_mtime = 0.0
        self.data = self._load()

    def reload_if_changed(self):
        """若检测到本地 configs.json 发生变动（如被外部编辑或 Git 变更），自动重新读取最新配置"""
        if os.path.exists(DATA_FILE):
            try:
                mtime = os.path.getmtime(DATA_FILE)
                if mtime > self._last_mtime:
                    with open(DATA_FILE, "r", encoding="utf-8") as f:
                        self.data = json.load(f)
                        self._last_mtime = mtime
                        print(f"[ConfigStore] 检测到 {DATA_FILE} 更新，已热重载最新配置到内存")
            except Exception as e:
                print(f"[ConfigStore] 自动热重载 {DATA_FILE} 异常: {e}")

    def _load(self) -> Dict[str, Any]:
        overwrite = should_overwrite_on_startup()
        if not overwrite and os.path.exists(DATA_FILE):
            try:
                with open(DATA_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self._last_mtime = os.path.getmtime(DATA_FILE)
                    print(f"[ConfigStore] 成功从本地运行时文件 {DATA_FILE} 加载数据")
                    return data
            except Exception as e:
                print(f"[ConfigStore] 读取 {DATA_FILE} 失败，回退使用 config.py 默认配置: {e}")

        defaults = get_default_configs()
        print("[ConfigStore] 使用 config.py 预设配置初始化仓储并持久化到本地...")
        self._save_to_file(defaults)
        return defaults

    def _save_to_file(self, data: Dict[str, Any]):
        try:
            with open(DATA_FILE, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            self._last_mtime = os.path.getmtime(DATA_FILE)
        except Exception as e:
            print(f"[ConfigStore] 写入 {DATA_FILE} 异常: {e}")

    def save(self):
        self._save_to_file(self.data)

    def reset(self):
        """重置为 config.py 中配置的默认数据"""
        try:
            import config
            importlib.reload(config)
        except Exception:
            pass

        self.data = get_default_configs()
        self.save()
        return self.data

    def get_all(self) -> Dict[str, Any]:
        self.reload_if_changed()
        return self.data

    def get_category(self, category: str) -> List[Any]:
        self.reload_if_changed()
        return self.data.get(category, [])

    def save_item(self, category: str, item: Dict[str, Any], key_field: str = "name") -> Dict[str, Any]:
        if category not in self.data:
            self.data[category] = []
        key_val = item.get(key_field)
        items = self.data[category]

        updated = False
        for idx, existing in enumerate(items):
            match = False
            if key_val is not None and existing.get(key_field) == key_val:
                match = True
            elif category == "models":
                item_code = item.get("code")
                item_name = item.get("name")
                if item_code and (existing.get("code") == item_code or existing.get("name") == item_code):
                    match = True
                elif item_name and (existing.get("name") == item_name or existing.get("code") == item_name):
                    match = True
            elif category == "strategies":
                item_sid = item.get("strategyId")
                if item_sid and existing.get("strategyId") == item_sid:
                    match = True

            if match:
                items[idx] = item
                updated = True
                break

        if not updated:
            items.append(item)

        self.save()
        return item

    def delete_item(self, category: str, key_val: str, key_field: str = "name") -> bool:
        if category not in self.data:
            return False
        before_len = len(self.data[category])

        def is_match(existing):
            if existing.get(key_field) == key_val:
                return True
            if category == "models" and (existing.get("code") == key_val or existing.get("name") == key_val):
                return True
            if category == "strategies" and existing.get("strategyId") == key_val:
                return True
            return False

        self.data[category] = [x for x in self.data[category] if not is_match(x)]
        if len(self.data[category]) != before_len:
            self.save()
            return True
        return False


config_store = ConfigStore()
