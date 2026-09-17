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
        "prompts": [],
        "promptGroups": [],
    }


def should_overwrite_on_startup() -> bool:
    cfg = load_config_module()
    if cfg and hasattr(cfg, "OVERWRITE_DATA_WITH_CONFIG"):
        return bool(cfg.OVERWRITE_DATA_WITH_CONFIG)
    return False


class ConfigStore:
    def __init__(self):
        os.makedirs(DATA_DIR, exist_ok=True)
        self.data = self._load()

    def _load(self) -> Dict[str, Any]:
        overwrite = should_overwrite_on_startup()
        if not overwrite and os.path.exists(DATA_FILE):
            try:
                with open(DATA_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
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
        except Exception as e:
            print(f"[ConfigStore] 写入 {DATA_FILE} 异常: {e}")

    def save(self):
        self._save_to_file(self.data)

    def reset(self):
        """重置为 config.py 中配置的默认数据"""
        # 尝试重载 config 模块以获取 config.py 的最新变动
        try:
            import config
            importlib.reload(config)
        except Exception:
            pass

        self.data = get_default_configs()
        self.save()
        return self.data

    def get_all(self) -> Dict[str, Any]:
        return self.data

    def get_category(self, category: str) -> List[Any]:
        return self.data.get(category, [])

    def save_item(self, category: str, item: Dict[str, Any], key_field: str = "name") -> Dict[str, Any]:
        if category not in self.data:
            self.data[category] = []
        key_val = item.get(key_field)
        items = self.data[category]

        updated = False
        for idx, existing in enumerate(items):
            if existing.get(key_field) == key_val:
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
        self.data[category] = [x for x in self.data[category] if x.get(key_field) != key_val]
        if len(self.data[category]) != before_len:
            self.save()
            return True
        return False


config_store = ConfigStore()
