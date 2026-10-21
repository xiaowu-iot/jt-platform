/* 指令与调试：指令下发、下发记录、报文监视 */
(function () {
  const { ref, reactive, computed, onMounted, onUnmounted, watch } = Vue;
  const { api, fail, ok, fmt, has, qs, STATE, VehiclePicker } = JT;

  /** 常用参数 ID，参数设置时给个下拉 */
  const PARAMS = [
    ['0x0001', '心跳间隔（秒）'], ['0x0002', 'TCP 应答超时（秒）'], ['0x0003', 'TCP 重传次数'],
    ['0x0013', '主服务器地址'], ['0x0018', '服务器 TCP 端口'], ['0x0020', '位置汇报策略'], ['0x0021', '位置汇报方案'],
    ['0x0027', '休眠时汇报间隔（秒）'], ['0x0028', '紧急报警时汇报间隔（秒）'], ['0x0029', '缺省时间汇报间隔（秒）'],
    ['0x002C', '缺省距离汇报间隔（米）'], ['0x0055', '最高速度（km/h）'], ['0x0056', '超速持续时间（秒）'],
    ['0x0057', '连续驾驶时间门限（秒）'], ['0x0058', '当天累计驾驶时间门限（秒）'], ['0x0059', '最小休息时间（秒）'],
    ['0x005A', '最长停车时间（秒）'], ['0x0080', '车辆里程表读数（1/10 km）'], ['0x0083', '车牌号'], ['0x0084', '车牌颜色'],
  ];

  /** 指令目录：每条指令的接口、参数表单 */
  const COMMANDS = [
    { k: 'location', t: '位置查询', id: '0x8201', d: '立即上报一次当前位置', m: 'GET', p: '/location' },
    { k: 'params', t: '查询参数', id: '0x8104', d: '查询终端全部参数', m: 'GET', p: '/params' },
    { k: 'setParams', t: '设置参数', id: '0x8103', d: '修改心跳、上报间隔、服务器地址、限速等参数', m: 'PUT', p: '/params', form: 'params' },
    { k: 'attributes', t: '查询终端属性', id: '0x8107', d: '厂家、型号、固件版本、ICCID', m: 'GET', p: '/attributes' },
    { k: 'text', t: '文本下发', id: '0x8300', d: '在终端屏幕显示，并用 TTS 播报', m: 'POST', p: '/text', fields: [{ k: 'text', t: '内容', type: 'textarea' }, { k: 'urgent', t: '紧急', type: 'switch' }] },
    { k: 'tracking', t: '临时位置跟踪', id: '0x8202', d: '在有效期内按指定间隔上报，到期恢复原来的策略', m: 'POST', p: '/tracking', fields: [{ k: 'interval', t: '间隔（秒）', type: 'number', v: 5 }, { k: 'valid', t: '有效期（秒）', type: 'number', v: 600 }] },
    { k: 'camera', t: '立即拍照', id: '0x8801', d: '拍照后终端用 0x0801 分包上传', m: 'POST', p: '/camera', fields: [{ k: 'channel', t: '通道', type: 'number', v: 1 }, { k: 'photos', t: '张数', type: 'number', v: 1 }] },
    { k: 'callBack', t: '电话回拨', id: '0x8400', d: '终端拨打指定号码，可以设为监听', m: 'POST', p: '/call-back', fields: [{ k: 'phone', t: '号码' }, { k: 'listen', t: '监听', type: 'switch' }] },
    { k: 'vehicle', t: '车门控制', id: '0x8500', d: '车门加锁 / 解锁（2013 和 2019 版格式不同，平台自动处理）', m: 'POST', p: '/vehicle-control', fields: [{ k: 'lock', t: '加锁', type: 'switch', v: true }] },
    { k: 'driver', t: '上报驾驶员', id: '0x8702', d: '终端上报当前插卡的驾驶员身份', m: 'POST', p: '/driver-info' },
    { k: 'linkCheck', t: '链路检测', id: '0x8204', d: '2019 版：检查链路是否正常', m: 'POST', p: '/link-check' },
    { k: 'control', t: '终端控制', id: '0x8105', d: '复位、恢复出厂、关闭连接等', m: 'POST', p: '/control', danger: true, fields: [{ k: 'command', t: '控制命令', type: 'select', v: 4, opts: [[4, '4 终端复位'], [5, '5 恢复出厂设置'], [6, '6 关闭数据通信'], [7, '7 关闭所有无线通信']] }] },
  ];

  const Commands = {
    components: { VehiclePicker },
    setup() {
      const vehicleId = ref(null);
      const vehicle = ref(null);
      const cmd = ref(COMMANDS[0]);
      const form = reactive({});
      const params = ref([{ id: '0x0029', v: 30 }]);
      const result = ref(null);
      const sending = ref(false);
      const records = ref({ items: [], total: 0 });
      const page = ref(0);
      function pickCmd(c) {
        cmd.value = c; result.value = null;
        Object.keys(form).forEach(k => delete form[k]);
        (c.fields || []).forEach(f => { form[f.k] = f.v !== undefined ? f.v : (f.type === 'switch' ? false : ''); });
      }
      pickCmd(COMMANDS[0]);
      async function loadVehicle() {
        if (!vehicleId.value) { vehicle.value = null; return; }
        try { vehicle.value = await api('/vehicles/' + vehicleId.value); } catch (e) { fail(e); }
        loadRecords();
      }
      async function loadRecords() {
        try { records.value = await api('/commands?' + qs({ vehicleId: vehicleId.value, page: page.value, size: 10 })); } catch (e) { /* 忽略 */ }
      }
      watch(vehicleId, loadVehicle);
      onMounted(() => {
        const q = location.hash.split('?')[1];
        if (q && q.startsWith('id=')) vehicleId.value = Number(q.slice(3)); else loadRecords();
      });
      async function send() {
        if (!vehicle.value) { fail(new Error('请选择车辆')); return; }
        if (!vehicle.value.deviceId) { fail(new Error('这辆车没有绑定终端')); return; }
        const c = cmd.value;
        if (c.danger) { try { await JT.ElMessageBox.confirm('确定给 ' + vehicle.value.plate + ' 下发「' + c.t + '」？', '确认', { type: 'warning' }); } catch (e) { return; } }
        let body;
        if (c.form === 'params') { body = {}; params.value.filter(p => p.id).forEach(p => { body[p.id] = isNaN(Number(p.v)) || p.v === '' ? p.v : Number(p.v); }); }
        else if (c.fields) body = { ...form };
        sending.value = true; result.value = null;
        const t0 = Date.now();
        try {
          const r = await api('/devices/' + vehicle.value.deviceId + c.p, { method: c.m, body: c.m === 'GET' ? undefined : (body || {}) });
          result.value = { ok: true, ms: Date.now() - t0, data: r };
        } catch (e) { result.value = { ok: false, ms: Date.now() - t0, data: e.message }; }
        finally { sending.value = false; loadRecords(); }
      }
      const STATUS = { SUCCESS: ['成功', 'success'], FAILED: ['失败', 'danger'], TIMEOUT: ['超时', 'warning'], OFFLINE: ['不在线', 'info'], SENDING: ['等待应答', 'primary'] };
      return { vehicleId, vehicle, cmd, form, params, result, sending, records, page, COMMANDS, PARAMS, STATUS, pickCmd, send, loadRecords, fmt, STATE };
    },
    template: `<div class="cmd">
      <div class="panel cmd-list">
        <div class="panel-title">指令</div>
        <a v-for="c in COMMANDS" :key="c.k" :class="{on: c===cmd}" @click="pickCmd(c)"><b>{{ c.t }}</b><small>{{ c.id }}</small></a>
      </div>
      <div class="cmd-main">
        <div class="panel">
          <div class="toolbar"><vehicle-picker v-model="vehicleId" width="220px"/>
            <template v-if="vehicle"><span class="st-dot" :style="{'--c': STATE[vehicle.state].c}">{{ STATE[vehicle.state].t }}</span><span class="muted">终端 {{ vehicle.deviceId }} · JT/T 808-{{ vehicle.protocolVersion || '?' }} · {{ vehicle.terminalModel || '' }}</span></template></div>
          <div class="cmd-head"><b>{{ cmd.t }}</b><el-tag size="small">{{ cmd.id }}</el-tag><span class="muted">{{ cmd.d }}</span></div>
          <el-form label-width="100px" style="max-width:560px" v-if="cmd.fields">
            <el-form-item v-for="f in cmd.fields" :key="f.k" :label="f.t">
              <el-switch v-if="f.type==='switch'" v-model="form[f.k]"/>
              <el-input-number v-else-if="f.type==='number'" v-model="form[f.k]" :min="0"/>
              <el-select v-else-if="f.type==='select'" v-model="form[f.k]"><el-option v-for="o in f.opts" :key="o[0]" :value="o[0]" :label="o[1]"/></el-select>
              <el-input v-else v-model="form[f.k]" :type="f.type==='textarea' ? 'textarea' : 'text'" :rows="3"/>
            </el-form-item>
          </el-form>
          <div v-if="cmd.form==='params'" class="param-rows">
            <div v-for="(p,i) in params" :key="i" class="param-row">
              <el-select v-model="p.id" filterable allow-create placeholder="参数 ID" style="width:280px"><el-option v-for="o in PARAMS" :key="o[0]" :value="o[0]" :label="o[0] + ' ' + o[1]"/></el-select>
              <el-input v-model="p.v" placeholder="值" style="width:200px"/><el-button link type="danger" @click="params.splice(i,1)">删除</el-button>
            </div>
            <el-button size="small" @click="params.push({id:'', v:''})">＋ 添加参数</el-button>
          </div>
          <div class="mt"><el-button type="primary" :loading="sending" :disabled="!vehicle" @click="send"><icon name="send" :size="15"/>&nbsp;下发</el-button></div>
          <div v-if="result" class="cmd-result" :class="{bad: !result.ok}">
            <div class="muted">{{ result.ok ? '终端应答' : '失败' }} · 耗时 {{ result.ms }} ms</div>
            <pre>{{ typeof result.data === 'string' ? result.data : JSON.stringify(result.data, null, 2) }}</pre>
          </div>
        </div>
        <div class="panel">
          <div class="panel-title">下发记录<small>{{ vehicle ? vehicle.plate : '全部车辆' }}</small></div>
          <el-table :data="records.items" size="small">
            <el-table-column label="时间" width="150"><template #default="{row}">{{ fmt(row.createdAt, true) }}</template></el-table-column>
            <el-table-column prop="plate" label="车牌" width="110"/>
            <el-table-column label="指令" width="160"><template #default="{row}">{{ row.name }}</template></el-table-column>
            <el-table-column prop="params" label="参数" min-width="160" show-overflow-tooltip/>
            <el-table-column label="结果" width="90"><template #default="{row}"><el-tag size="small" :type="STATUS[row.status][1]">{{ STATUS[row.status][0] }}</el-tag></template></el-table-column>
            <el-table-column prop="result" label="应答" min-width="200" show-overflow-tooltip/>
            <el-table-column label="耗时" width="80"><template #default="{row}">{{ row.costMillis }} ms</template></el-table-column>
            <el-table-column prop="operator" label="操作人" width="90"/>
          </el-table>
          <div class="pager"><el-pagination layout="total, prev, pager, next" :total="records.total" :page-size="10" :current-page="page + 1" @current-change="p => { page = p - 1; loadRecords(); }"/></div>
        </div>
      </div>
    </div>`,
  };

  // ===================== 报文监视 =====================
  const Raw = {
    setup() {
      const q = location.hash.split('?')[1];
      const device = ref(q && q.startsWith('device=') ? q.slice(7) : '');
      const minutes = ref(10);
      const list = ref([]);
      const auto = ref(true);
      const traced = ref([]);
      let timer;
      async function load() {
        if (!device.value.trim()) return;
        try { list.value = (await api('/access/raw/' + device.value.trim() + '?minutes=' + minutes.value)).reverse(); } catch (e) { fail(e); }
      }
      async function trace(on) {
        try { traced.value = (await api('/access/raw/' + device.value.trim() + '/trace?on=' + on, { method: 'POST' })).traced; ok(on ? '已开启，报文全部写入 jt-raw.log' : '已关闭'); } catch (e) { fail(e); }
      }
      onMounted(() => { load(); timer = setInterval(() => auto.value && load(), 3000); });
      onUnmounted(() => clearInterval(timer));
      const spaced = h => h.replace(/(..)/g, '$1 ').trim();
      return { device, minutes, list, auto, traced, load, trace, fmt, spaced };
    },
    template: `<div class="panel">
      <div class="toolbar">
        <el-input v-model="device" placeholder="终端号（设备号）" style="width:200px" @keyup.enter="load"/>
        <el-select v-model="minutes" style="width:120px"><el-option v-for="m in [5,10,30,60]" :key="m" :value="m" :label="'最近 ' + m + ' 分钟'"/></el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-checkbox v-model="auto">自动刷新</el-checkbox>
        <span class="grow"></span>
        <el-button size="small" @click="trace(true)">写入日志</el-button><el-button size="small" @click="trace(false)">停止写入</el-button>
      </div>
      <div class="muted" style="margin-bottom:8px">记录的是每次 TCP 读写的原始字节（拆包、粘包都原样保留），按 7E 切开后标出消息名称。内存里每台终端保留最近 200 条。</div>
      <div class="raw-list">
        <div v-for="(r,i) in list" :key="i" class="raw" :class="r.direction">
          <div class="raw-head"><b>{{ r.direction==='IN' ? '终端 → 平台' : '平台 → 终端' }}</b><span>{{ fmt(r.time, true) }}</span><span class="muted">{{ r.remote }}</span></div>
          <div v-for="(f,j) in r.frames" :key="j" class="frame"><el-tag size="small" :type="r.direction==='IN' ? 'success' : 'primary'">{{ f.name }}</el-tag><code>{{ spaced(f.hex) }}</code><a class="raw-go" :href="'#/proto-inspect?hex=' + f.hex">解析</a></div>
          <code v-if="!r.frames.length">{{ spaced(r.hex) }}</code>
        </div>
        <div v-if="!list.length" class="empty">输入终端号查看最近的收发报文</div>
      </div>
    </div>`,
  };

  Object.assign(JT.Pages, { Commands, Raw });
})();
