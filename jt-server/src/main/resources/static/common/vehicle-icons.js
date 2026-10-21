/*
 * 地图车标：按车型画俯视的小车，车身颜色表示状态，整车跟着方向角转（车头朝行驶方向）。
 * 后台、大屏、移动端共用：VehicleIcons.svg(type, color, dir, size)
 * 图形画在 40×40 的格子里，中心 (20,20)，车头朝上（正北）。
 */
(function () {
  const GLASS = '#1f3550';

  /** 颜色变亮（k>0）或变暗（k<0） */
  function shade(hex, k) {
    const n = parseInt(hex.slice(1), 16);
    const f = x => Math.max(0, Math.min(255, Math.round(k > 0 ? x + (255 - x) * k : x * (1 + k))));
    return 'rgb(' + f(n >> 16) + ',' + f(n >> 8 & 255) + ',' + f(n & 255) + ')';
  }

  /** 卡车车头：驾驶室 + 前挡风 + 两侧后视镜，后面接货箱 */
  function truck(c, D, box) {
    return `<rect x="13.5" y="4" width="13" height="9" rx="3.5" fill="${c}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M15 6.6h10l-1 2.6h-8z" fill="${GLASS}"/>`
      + `<rect x="12" y="7.2" width="1.6" height="1.8" rx=".5" fill="${D}"/><rect x="26.4" y="7.2" width="1.6" height="1.8" rx=".5" fill="${D}"/>` + box;
  }

  /** 小轿车：前后挡风、车顶、后视镜 */
  function car(c, L, D) {
    return `<path d="M20 6c4.2 0 6.3 1.4 6.5 5l.3 18c0 3.6-2.2 5-6.8 5s-6.8-1.4-6.8-5l.3-18c.2-3.6 2.3-5 6.5-5z" fill="${c}" stroke="${D}" stroke-width=".8"/>`
      + `<rect x="12" y="12.4" width="1.8" height="1.6" rx=".5" fill="${D}"/><rect x="26.2" y="12.4" width="1.8" height="1.6" rx=".5" fill="${D}"/>`
      + `<path d="M15.4 13.4c2.6-1.4 6.6-1.4 9.2 0l-.8 3.4h-7.6z" fill="${GLASS}"/>`
      + `<rect x="16.2" y="17.6" width="7.6" height="8.8" rx="1.5" fill="${L}"/>`
      + `<path d="M16.2 30.6c2.2.9 5.4.9 7.6 0l-.6-2.6h-6.4z" fill="${GLASS}"/>`;
  }

  const SHAPES = {
    // 物流车：方货箱
    LOGISTICS: (c, L, D) => truck(c, D, `<rect x="13" y="14" width="14" height="22" rx="1.5" fill="${L}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M16 16v18M20 16v18M24 16v18" stroke="${c}" stroke-width=".7" opacity=".6"/>`),
    // 冷链车：白色保温箱，前头制冷机组，箱顶雪花
    COLD_CHAIN: (c, L, D) => truck(c, D, `<rect x="13" y="14" width="14" height="22" rx="1.5" fill="#f4f8fc" stroke="${D}" stroke-width=".8"/>`
      + `<rect x="15" y="15" width="10" height="3" rx=".8" fill="${c}"/>`
      + `<path d="M20 21v10M15.8 23.5l8.4 5M15.8 28.5l8.4-5" stroke="${c}" stroke-width="1.3" stroke-linecap="round"/>`),
    // 垃圾收运车：压缩箱，尾部是深色的填料口，带警示斜纹
    GARBAGE: (c, L, D) => truck(c, D, `<rect x="13" y="14" width="14" height="17" rx="2" fill="${L}" stroke="${D}" stroke-width=".8"/>`
      + `<rect x="12.5" y="30.5" width="15" height="6" rx="1.5" fill="${D}"/>`
      + `<path d="M14.5 33.5l2-2M18 35l3.5-3.5M23 35l2.5-2.5" stroke="#facc15" stroke-width="1.1" stroke-linecap="round"/>`
      + `<path d="M16 17.5h8M16 21h8M16 24.5h8" stroke="${c}" stroke-width=".9" opacity=".7"/>`),
    // 垃圾直运车：自带的长压缩箱（比收运车长），尾部填料口，箱上一道压缩板
    DIRECT: (c, L, D) => truck(c, D, `<rect x="12.5" y="13.5" width="15" height="19" rx="2" fill="${L}" stroke="${D}" stroke-width=".8"/>`
      + `<rect x="12.5" y="31.5" width="15" height="5.5" rx="1.5" fill="${D}"/>`
      + `<path d="M14.5 34.3l2-2M18 35.8l3.5-3.5M23 35.8l2.5-2.5" stroke="#facc15" stroke-width="1.1" stroke-linecap="round"/>`
      + `<rect x="14.5" y="22" width="11" height="2.2" rx=".6" fill="${c}" opacity=".85"/>`
      + `<path d="M16 16.5h8M16 19.2h8M16 27h8M16 29.5h8" stroke="${c}" stroke-width=".8" opacity=".6"/>`),
    // 垃圾转运车：长集装箱 + 前端钩臂
    TRANSFER: (c, L, D) => truck(c, D, `<rect x="16.5" y="13" width="7" height="2.4" rx=".6" fill="${D}"/>`
      + `<rect x="12.5" y="15" width="15" height="22.5" rx="1.2" fill="${L}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M15.5 17v18.5M18.5 17v18.5M21.5 17v18.5M24.5 17v18.5" stroke="${c}" stroke-width=".8" opacity=".55"/>`),
    // 渣土车：带横筋的翻斗
    MUCK: (c, L, D) => truck(c, D, `<path d="M12.5 14h15l-1 22h-13z" fill="${L}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M14.5 18h11M14.5 22h11M14.5 26h11M14.7 30h10.6" stroke="${D}" stroke-width=".8" opacity=".55"/>`),
    // 环卫车：圆罐
    SANITATION: (c, L, D) => truck(c, D, `<rect x="13" y="14" width="14" height="22" rx="7" fill="${L}" stroke="${D}" stroke-width=".8"/>`
      + `<rect x="17" y="16" width="6" height="18" rx="3" fill="${c}" opacity=".45"/>`),
    // 危化品车：圆罐 + 橙色菱形标志
    DANGEROUS: (c, L, D) => truck(c, D, `<rect x="13" y="14" width="14" height="22" rx="7" fill="${L}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M20 20l4.5 5-4.5 5-4.5-5z" fill="#f97316" stroke="#fff" stroke-width=".8"/>`),
    // 客车：长车身，前挡风，车顶空调
    BUS: (c, L, D) => `<rect x="13" y="3" width="14" height="34" rx="4" fill="${c}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M14.6 5.6h10.8l-.8 3H15.4z" fill="${GLASS}"/><rect x="15.5" y="11" width="9" height="22" rx="1.5" fill="${L}"/>`
      + `<rect x="17.5" y="14" width="5" height="5" rx="1" fill="${D}" opacity=".5"/>`,
    // 校车：客车车身，车顶一道黄色标识带
    SCHOOL_BUS: (c, L, D) => `<rect x="13" y="4" width="14" height="32" rx="4" fill="${c}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M14.6 6.6h10.8l-.8 3H15.4z" fill="${GLASS}"/><rect x="15.5" y="12" width="9" height="21" rx="1.5" fill="${L}"/>`
      + `<rect x="13.6" y="19" width="12.8" height="4" fill="#facc15"/><rect x="13.6" y="27" width="12.8" height="2" fill="#facc15"/>`,
    // 救护车：厢式车，车顶白底红十字，前面一排警灯
    AMBULANCE: (c, L, D) => `<rect x="13" y="4" width="14" height="32" rx="3.5" fill="${c}" stroke="${D}" stroke-width=".8"/>`
      + `<path d="M14.6 7h10.8l-.8 3.2H15.4z" fill="${GLASS}"/>`
      + `<rect x="15" y="11.5" width="4.5" height="2" rx=".5" fill="#ef4444"/><rect x="20.5" y="11.5" width="4.5" height="2" rx=".5" fill="#3b82f6"/>`
      + `<rect x="14.5" y="16" width="11" height="17.5" rx="1.5" fill="#fff"/>`
      + `<path d="M18.6 20.2h2.8v2.8h2.8v2.8h-2.8v2.8h-2.8v-2.8h-2.8v-2.8h2.8z" fill="#ef4444"/>`
      + `<rect x="12" y="8.6" width="1.6" height="1.8" rx=".5" fill="${D}"/><rect x="26.4" y="8.6" width="1.6" height="1.8" rx=".5" fill="${D}"/>`,
    // 出租车：轿车 + 顶灯
    TAXI: (c, L, D) => car(c, L, D) + `<rect x="17.5" y="19" width="5" height="2.4" rx=".6" fill="#facc15" stroke="${D}" stroke-width=".5"/>`,
    // 其他：轿车
    OTHER: (c, L, D) => car(c, L, D)
  };

  /**
   * @param type 车型（LOGISTICS / COLD_CHAIN / MUCK / SANITATION / DANGEROUS / BUS / TAXI / OTHER）
   * @param c    车身颜色（状态色，#rrggbb）
   * @param dir  方向角，正北 0，顺时针
   * @param size 像素大小
   */
  function svg(type, c, dir, size) {
    const shape = SHAPES[type] || SHAPES.OTHER;
    return `<svg class="vh" width="${size}" height="${size}" viewBox="0 0 40 40">`
      + `<g class="dir" style="transform:rotate(${Math.round(dir || 0)}deg);transform-origin:20px 20px">`
      + shape(c, shade(c, .35), shade(c, -.25)) + `</g></svg>`;
  }

  /**
   * 轨迹按车速着色，以这辆车的限速为准：绿色正常，黄色接近限速（限速的 90% 以上），红色超速，灰色停车（≤1 km/h）
   */
  const SPEED_COLORS = { normal: '#22c55e', near: '#eab308', over: '#dc2626', stop: '#94a3b8' };
  function speedColor(v, limit) {
    if (v <= 1) return SPEED_COLORS.stop;
    const l = limit > 0 ? limit : 100;
    return v > l ? SPEED_COLORS.over : v >= l * 0.9 ? SPEED_COLORS.near : SPEED_COLORS.normal;
  }

  /**
   * 两个相邻轨迹点之间是不是断档：
   * 超过 2 分钟没数据且挪了 500 米以上；或者两点间折算的速度超过 160 km/h；
   * 或者中间缺了数据（隔了 20 秒以上），折算速度又明显比终端报的车速快（车不可能开这么快，是瞬移了）。
   * 断档那一段画成灰色虚线，不按车速着色，免得看起来像车沿直线开过去了
   * @param a、b {lat, lng, sec, s}，sec 是秒，s 是终端上报的车速 km/h
   */
  function isGap(a, b) {
    const dt = Math.abs(b.sec - a.sec);
    const dy = (b.lat - a.lat) * 111320, dx = (b.lng - a.lng) * 111320 * Math.cos(a.lat * Math.PI / 180);
    const m = Math.sqrt(dx * dx + dy * dy);
    const kmh = dt > 0 ? m / dt * 3.6 : 0;
    const reported = ((a.s || 0) + (b.s || 0)) / 2;
    return (dt > 120 && m > 500) || kmh > 160 || (dt >= 20 && m > 300 && kmh > reported * 1.5 + 20);
  }
  const GAP_STYLE = { color: '#94a3b8', weight: 3, opacity: .9, dashArray: '6 8' };

  window.VehicleIcons = { svg, speedColor, SPEED_COLORS, isGap, GAP_STYLE, types: Object.keys(SHAPES) };
})();
