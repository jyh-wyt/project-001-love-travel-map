export type CityRecord = {
  id: string;
  author: string;
  date: string;
  text: string;
  images: string[];
};

export type City = {
  code: string;
  name: string;
  visited: boolean;
  cover: string;
  dateRange: string;
  records: CityRecord[];
};

export type Province = {
  code: string;
  name: string;
  x: number;
  y: number;
  cities: City[];
};

function emptyCity(code: string, name: string): City {
  return {
    code,
    name,
    visited: false,
    cover: "",
    dateRange: "",
    records: []
  };
}

export const provinces: Province[] = [
  {
    code: "110000",
    name: "北京",
    x: 69,
    y: 28,
    cities: [emptyCity("110100", "北京")]
  },
  {
    code: "120000",
    name: "天津",
    x: 72,
    y: 31,
    cities: [emptyCity("120100", "天津")]
  },
  {
    code: "130000",
    name: "河北",
    x: 69,
    y: 34,
    cities: [emptyCity("130100", "石家庄")]
  },
  {
    code: "140000",
    name: "山西",
    x: 64,
    y: 36,
    cities: [emptyCity("140100", "太原")]
  },
  {
    code: "150000",
    name: "内蒙古",
    x: 58,
    y: 20,
    cities: [emptyCity("150100", "呼和浩特")]
  },
  {
    code: "210000",
    name: "辽宁",
    x: 82,
    y: 24,
    cities: [emptyCity("210100", "沈阳")]
  },
  {
    code: "220000",
    name: "吉林",
    x: 86,
    y: 18,
    cities: [emptyCity("220100", "长春")]
  },
  {
    code: "230000",
    name: "黑龙江",
    x: 86,
    y: 10,
    cities: [emptyCity("230100", "哈尔滨")]
  },
  {
    code: "310000",
    name: "上海",
    x: 79,
    y: 55,
    cities: [
      {
        code: "310100",
        name: "上海",
        visited: true,
        cover:
          "https://images.unsplash.com/photo-1538428494232-9c0d8a3ab403?auto=format&fit=crop&w=900&q=80",
        dateRange: "2025年10月1日 至 2025年10月4日",
        records: [
          {
            id: "shanghai-1",
            author: "她",
            date: "2025年10月2日",
            text: "晚上从外滩往回走，灯光映在水面上。那天我们没有赶行程，只是安静地看了很久。",
            images: [
              "https://images.unsplash.com/photo-1538428494232-9c0d8a3ab403?auto=format&fit=crop&w=900&q=80"
            ]
          }
        ]
      }
    ]
  },
  {
    code: "320000",
    name: "江苏",
    x: 76,
    y: 52,
    cities: [emptyCity("320100", "南京")]
  },
  {
    code: "330000",
    name: "浙江",
    x: 76,
    y: 60,
    cities: [emptyCity("330100", "杭州")]
  },
  {
    code: "340000",
    name: "安徽",
    x: 72,
    y: 55,
    cities: [emptyCity("340100", "合肥")]
  },
  {
    code: "350000",
    name: "福建",
    x: 73,
    y: 68,
    cities: [emptyCity("350100", "福州")]
  },
  {
    code: "360000",
    name: "江西",
    x: 68,
    y: 64,
    cities: [emptyCity("360100", "南昌")]
  },
  {
    code: "370000",
    name: "山东",
    x: 74,
    y: 38,
    cities: [
      {
        code: "370200",
        name: "青岛",
        visited: true,
        cover:
          "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
        dateRange: "2026年6月3日 至 2026年6月5日",
        records: [
          {
            id: "qingdao-1",
            author: "我",
            date: "2026年6月3日",
            text: "傍晚到了海边，风很轻。我们沿着栈桥慢慢走，觉得这一段路以后应该会反复想起。",
            images: [
              "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=80",
              "https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=900&q=80"
            ]
          }
        ]
      },
      emptyCity("370100", "济南")
    ]
  },
  {
    code: "410000",
    name: "河南",
    x: 66,
    y: 47,
    cities: [emptyCity("410100", "郑州")]
  },
  {
    code: "420000",
    name: "湖北",
    x: 63,
    y: 56,
    cities: [emptyCity("420100", "武汉")]
  },
  {
    code: "430000",
    name: "湖南",
    x: 61,
    y: 64,
    cities: [emptyCity("430100", "长沙")]
  },
  {
    code: "440000",
    name: "广东",
    x: 63,
    y: 76,
    cities: [emptyCity("440100", "广州")]
  },
  {
    code: "450000",
    name: "广西",
    x: 54,
    y: 75,
    cities: [emptyCity("450100", "南宁")]
  },
  {
    code: "460000",
    name: "海南",
    x: 57,
    y: 86,
    cities: [emptyCity("460100", "海口")]
  },
  {
    code: "500000",
    name: "重庆",
    x: 52,
    y: 59,
    cities: [emptyCity("500100", "重庆")]
  },
  {
    code: "510000",
    name: "四川",
    x: 47,
    y: 58,
    cities: [emptyCity("510100", "成都")]
  },
  {
    code: "520000",
    name: "贵州",
    x: 51,
    y: 68,
    cities: [emptyCity("520100", "贵阳")]
  },
  {
    code: "530000",
    name: "云南",
    x: 42,
    y: 72,
    cities: [emptyCity("530100", "昆明")]
  },
  {
    code: "540000",
    name: "西藏",
    x: 28,
    y: 56,
    cities: [emptyCity("540100", "拉萨")]
  },
  {
    code: "610000",
    name: "陕西",
    x: 58,
    y: 45,
    cities: [emptyCity("610100", "西安")]
  },
  {
    code: "620000",
    name: "甘肃",
    x: 47,
    y: 40,
    cities: [emptyCity("620100", "兰州")]
  },
  {
    code: "630000",
    name: "青海",
    x: 39,
    y: 42,
    cities: [emptyCity("630100", "西宁")]
  },
  {
    code: "640000",
    name: "宁夏",
    x: 53,
    y: 36,
    cities: [emptyCity("640100", "银川")]
  },
  {
    code: "650000",
    name: "新疆",
    x: 18,
    y: 24,
    cities: [emptyCity("650100", "乌鲁木齐")]
  },
  {
    code: "710000",
    name: "台湾",
    x: 78,
    y: 74,
    cities: [emptyCity("710100", "台北")]
  },
  {
    code: "810000",
    name: "香港",
    x: 64,
    y: 80,
    cities: [emptyCity("810100", "香港")]
  },
  {
    code: "820000",
    name: "澳门",
    x: 62,
    y: 81,
    cities: [emptyCity("820100", "澳门")]
  }
];

export function findCity(cityCode: string) {
  for (const province of provinces) {
    const city = province.cities.find((item) => item.code === cityCode);
    if (city) {
      return { province, city };
    }
  }
  return null;
}

export function findProvince(provinceCode: string) {
  return provinces.find((province) => province.code === provinceCode) ?? null;
}

export function findCityInProvince(provinceCode: string, cityCode: string, cityName: string) {
  const province = findProvince(provinceCode);
  if (!province) {
    return undefined;
  }

  return province.cities.find(
    (city) => city.code === cityCode || normalizeCityName(city.name) === normalizeCityName(cityName)
  );
}

export function visitedCities() {
  return provinces.flatMap((province) =>
    province.cities
      .filter((city) => city.records.length > 0)
      .map((city) => ({ province, city }))
  );
}

function normalizeCityName(name: string) {
  return name.replace(/[市区县盟州地区]$/, "");
}
