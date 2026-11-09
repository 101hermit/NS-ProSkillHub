```python
#!/usr/bin/env python3
"""
供应商查找脚本
根据地区、材料类型和关键词筛选供应商数据
"""

import json
import os
import sys
import argparse
from pathlib import Path


# 供应商示例数据库
# 实际使用时可将数据存放在 data/suppliers.json 文件中
SAMPLE_SUPPLIERS = [
    {
        "name": "深圳鸿达包装有限公司",
        "type": "packaging",
        "category": "纸箱/彩盒",
        "address": "深圳市宝安区沙井街道新桥工业园A栋",
        "contact": {
            "phone": "0755-27288888",
            "mobile": "13828889999",
            "email": "info@hongdapacking.com",
            "website": "www.hongdapacking.com"
        },
        "main_products": ["瓦楞纸箱", "彩盒", "礼品盒", "展示架"],
        "region": "珠三角",
        "city": "深圳",
        "province": "广东"
    },
    {
        "name": "东莞联丰塑料原料有限公司",
        "type": "raw",
        "category": "塑料原料",
        "address": "东莞市樟木头镇塑胶市场三期B区18号",
        "contact": {
            "phone": "0769-87716666",
            "mobile": "13926887777",
            "email": "sales@lianfengplastic.com",
            "website": "www.lianfengplastic.com"
        },
        "main_products": ["PP颗粒", "PE颗粒", "ABS树脂", "PC塑料"],
        "region": "珠三角",
        "city": "东莞",
        "province": "广东"
    },
    {
        "name": "广州越秀包装材料厂",
        "type": "packaging",
        "category": "塑料包装",
        "address": "广州市花都区新华工业区迎宾大道8号",
        "contact": {
            "phone": "020-36882222",
            "mobile": "13688886666",
            "email": "service@yuexiupack.com",
            "website": "www.yuexiupack.com"
        },
        "main_products": ["PE袋", "气泡膜", "缠绕膜", "快递袋"],
        "region": "珠三角",
        "city": "广州",
        "province": "广东"
    },
    {
        "name": "佛山南海金属材料有限公司",
        "type": "raw",
        "category": "金属材料",
        "address": "佛山市南海区狮山镇有色金属产业园",
        "contact": {
            "phone": "0757-86669999",
            "mobile": "13798886666",
            "email": "info@nanhaimetal.com",
            "website": "www.nanhaimetal.com"
        },
        "main_products": ["铝板", "铝卷", "不锈钢板", "镀锌板"],
        "region": "珠三角",
        "city": "佛山",
        "province": "广东"
    },
    {
        "name": "上海嘉定包装印刷厂",
        "type": "packaging",
        "category": "标签/印刷",
        "address": "上海市嘉定区安亭镇宝安公路5888号",
        "contact": {
            "phone": "021-59563333",
            "mobile": "13817775555",
            "email": "sales@jiadingprint.com",
            "website": "www.jiadingprint.com"
        },
        "main_products": ["不干胶标签", "防伪标签", "说明书", "画册"],
        "region": "长三角",
        "city": "上海",
        "province": "上海"
    },
    {
        "name": "昆山台达塑胶工业有限公司",
        "type": "raw",
        "category": "工程塑料",
        "address": "江苏省昆山市玉山镇高新区台达路1号",
        "contact": {
            "phone": "0512-57888888",
            "mobile": "13962668888",
            "email": "taida@tdplastic.com.cn",
            "website": "www.tdplastic.com.cn"
        },
        "main_products": ["POM", "PA66", "PC/ABS合金", "PBT"],
        "region": "长三角",
        "city": "昆山",
        "province": "江苏"
    },
    {
        "name": "苏州工业园区纸品包装有限公司",
        "type": "packaging",
        "category": "纸制品",
        "address": "苏州市工业园区唯亭街道星龙街288号",
        "contact": {
            "phone": "0512-62998888",
            "mobile": "13812669999",
            "email": "info@suzhoupaper.com",
            "website": "www.suzhoupaper.com"
        },
        "main_products": ["纸板", "纸箱", "蜂窝纸板", "纸护角"],
        "region": "长三角",
        "city": "苏州",
        "province": "江苏"
    },
    {
        "name": "成都兴达包装有限公司",
        "type": "packaging",
        "category": "综合包装",
        "address": "成都市新都区工业大道东段128号",
        "contact": {
            "phone": "028-83969999",
            "mobile": "13880887777",
            "email": "xingdapack@163.com",
            "website": "www.xingdapack.com"
        },
        "main_products": ["纸箱", "木箱", "托盘", "缓冲材料"],
        "region": "成渝",
        "city": "成都",
        "province": "四川"
    }
]

# 区域映射表（用于模糊查询）
REGION_MAPPING = {
    "珠三角": ["深圳", "东莞", "广州", "佛山", "珠海", "中山", "惠州", "江门", "肇庆"],
    "长三角": ["上海", "苏州", "昆山", "无锡", "常州", "南京", "杭州", "宁波", "嘉兴", "湖州", "绍兴"],
    "成渝": ["成都", "重庆", "绵阳", "德阳", "眉山"],
    "京津冀": ["北京", "天津", "石家庄", "唐山", "保定", "廊坊"],
    "华东": ["上海", "江苏", "浙江", "安徽", "福建", "江西", "山东"],
    "华南": ["广东", "广西", "海南"],
    "华北": ["北京", "天津", "河北", "山西", "内蒙古"],
    "华中": ["河南", "湖北", "湖南"],
    "西南": ["四川", "重庆", "贵州", "云南", "西藏"],
    "西北": ["陕西", "甘肃", "青海", "宁夏", "新疆"],
    "东北": ["辽宁", "吉林", "黑龙江"]
}


def load_suppliers(data_file: str = None) -> list:
    """加载供应商数据"""
    if data_file and os.path.exists(data_file):
        with open(data_file, 'r', encoding='utf-8') as f:
            return json.load(f)
    return SAMPLE_SUPPLIERS


def match_location(supplier: dict, location: str) -> bool:
    """判断供应商是否匹配指定地区"""
    location_lower = location.lower().strip()
    
    # 检查城市
    if supplier.get("city", "").lower() == location_lower:
        return True
    
    # 检查省份
    if supplier.get("province", "").lower() == location_lower:
        return True
    
    # 检查区域（如"珠三角"）
    if supplier.get("region", "").lower() == location_lower:
        return True
    
    # 检查区域映射（如输入"广东"时也匹配珠三角区域的供应商）
    if location_lower in REGION_MAPPING:
        if supplier.get("city") in REGION_MAPPING.get(location_lower, []):
            return True
    else:
        # 反向：如果输入的是具体城市，检查该城市是否属于某个区域
        for region, cities in REGION_MAPPING.items():
            if location_lower in [c.lower() for c in cities]:
                if supplier.get("region", "").lower() == region.lower():
                    return True
    
    # 地址模糊匹配
    if location_lower in supplier.get("address", "").lower():
        return True
    
    return False


def filter_suppliers(suppliers: list, location: str, material_type: str = None, keyword: str = None) -> list:
    """筛选供应商"""
    results = []
    
    for supplier in suppliers:
        # 地区匹配
        if not match_location(supplier, location):
            continue
        
        # 材料类型匹配
        if material_type and material_type not in ["all", ""]:
            if supplier.get("type") != material_type:
                continue
        
        # 关键词匹配（在名称、分类、产品中搜索）
        if keyword and keyword.strip():
            kw = keyword.lower().strip()
            text_to_search = (
                supplier.get("name", "").lower() + " " +
                supplier.get("category", "").lower() + " " +
                " ".join(supplier.get("main_products", [])).lower()
            )
            if kw not in text_to_search:
                continue
        
        results.append(supplier)
    
    return results


def main():
    parser = argparse.ArgumentParser(description="供应商查找工具")
    parser.add_argument("--location", required=True, help="供应商所在地")
    parser.add_argument("--material-type", default="all", choices=["raw", "packaging", "all"], 
                        help="材料类型：raw(原料) / packaging(包装)")
    parser.add_argument("--keyword", default="", help="关键词筛选")
    parser.add_argument("--data-file", help="自定义数据文件路径")
    
    args = parser.parse_args()
    
    # 加载供应商数据
    script_dir = Path(__file__).parent.parent
    default_data_file = script_dir / "data" / "suppliers.json"
    data_file = args.data_file or (str(default_data_file) if default_data_file.exists() else None)
    
    suppliers = load_suppliers(data_file)
    
    # 筛选
    results = filter_suppliers(
        suppliers, 
        args.location, 
        args.material_type if args.material_type != "all" else None,
        args.keyword
    )
    
    # 输出结果
    output = {
        "status": "success",
        "location": args.location,
        "material_type": args.material_type,
        "keyword": args.keyword if args.keyword else None,
        "count": len(results),
        "suppliers": results
    }
    
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()