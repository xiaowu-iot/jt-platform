/* 车辆监控平台 · 后台外框：登录、菜单、顶栏、报警提醒 */
(function () {
  const { createApp, reactive, ref, computed, onMounted, onUnmounted } = Vue;
  const { ElNotification } = ElementPlus;
  const { state, api, fail, ok, has, nowCN, Pages, Icon } = JT;

  const LoginView = {
    setup() {
      const form = reactive({ username: '', password: '' });
      const loading = ref(false);
      async function submit() {
        loading.value = true;
        try { state.me = await api('/auth/login', { method: 'POST', body: form }); }
        catch (e) { fail(e); } finally { loading.value = false; }
      }
      function demo(u) { form.username = u; form.password = state.options.demoPassword; submit(); }
      return { form, loading, submit, demo, state };
    },
    template: `<div class="login-wrap">
      <div class="login-hero">
        <div class="hero-grid"></div>
        <svg class="hero-roads" viewBox="0 0 800 600" preserveAspectRatio="xMidYMid slice">
          <path d="M-20 420 C 160 380, 240 260, 420 280 S 700 180, 840 120" class="road"/>
          <path d="M60 640 C 120 480, 260 420, 360 320 S 520 120, 600 -20" class="road"/>
          <path d="M-20 200 C 200 220, 360 160, 520 220 S 760 360, 840 340" class="road"/>
          <path d="M-20 420 C 160 380, 240 260, 420 280 S 700 180, 840 120" class="flow"/>
          <path d="M60 640 C 120 480, 260 420, 360 320 S 520 120, 600 -20" class="flow f2"/>
          <path d="M-20 200 C 200 220, 360 160, 520 220 S 760 360, 840 340" class="flow f3"/>
          <circle cx="420" cy="280" r="6" class="node"/><circle cx="360" cy="320" r="6" class="node"/><circle cx="520" cy="220" r="6" class="node"/>
        </svg>
        <div class="hero-text">
          <div class="hero-brand"><span class="logo-dot"><icon name="truck" :size="22"/></span>车辆智能监控平台</div>
          <h1>让每一辆车<br>都在掌握之中</h1>
          <p>基于 JT/T 808 协议：终端接入、实时定位、轨迹回放、报警处置、指令下发，三个版本终端混接。</p>
          <div class="hero-tags">
            <span>JT/T 808 2011 / 2013 / 2019</span><span>实时监控</span><span>轨迹回放</span><span>报警处置</span>
          </div>
        </div>
      </div>
      <div class="login-right">
        <div class="login-card">
          <h3>欢迎登录</h3>
          <div class="muted" style="margin:-14px 0 22px">车辆智能监控平台</div>
          <el-form @submit.prevent="submit" size="large">
            <el-form-item><el-input v-model="form.username" placeholder="用户名" autocomplete="username"/></el-form-item>
            <el-form-item><el-input v-model="form.password" type="password" placeholder="密码" show-password autocomplete="current-password"/></el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" class="login-btn">登 录</el-button>
          </el-form>
          <div v-if="state.options.demo" class="demo-accounts">
            <div class="muted">演示账号（密码 {{ state.options.demoPassword }}），点一下直接登录：</div>
            <el-button v-for="a in state.options.accounts" :key="a.username" size="small" @click="demo(a.username)">{{ a.label }}</el-button>
          </div>
          <div class="oss-links">开源地址：<a href="https://github.com/xiaowu-iot/jt-platform" target="_blank" rel="noopener">GitHub</a> · <a href="https://gitee.com/xiaowu-iot/jt-platform" target="_blank" rel="noopener">Gitee</a></div>
        </div>
      </div>
    </div>`,
  };

  const PAGES = [
    { k: 'dashboard', t: '运行概览', ic: 'chart', g: '监控中心', p: 'VEHICLE_VIEW' },
    { k: 'monitor', t: '实时监控', ic: 'map', g: '监控中心', p: 'VEHICLE_VIEW', full: true },
    { k: 'track', t: '轨迹回放', ic: 'track', g: '监控中心', p: 'VEHICLE_VIEW', full: true },
    { k: 'alarms', t: '报警中心', ic: 'alarm', g: '监控中心', p: 'ALARM_VIEW' },
    { k: 'vehicles', t: '车辆档案', ic: 'truck', g: '车辆管理', p: 'VEHICLE_VIEW' },
    { k: 'devices', t: '设备管理', ic: 'online', g: '车辆管理', p: 'VEHICLE_VIEW' },
    { k: 'fleets', t: '车队管理', ic: 'team', g: '车辆管理', p: 'VEHICLE_VIEW' },
    { k: 'sessions', t: '在线终端', ic: 'online', g: '车辆管理', p: 'RAW_VIEW' },
    { k: 'commands', t: '指令下发', ic: 'send', g: '指令调试', p: 'COMMAND_SEND' },
    { k: 'raw', t: '报文监视', ic: 'code', g: '指令调试', p: 'RAW_VIEW' },
    { k: 'notify', t: '通知配置', ic: 'bell', g: '系统设置', p: 'NOTIFY_MANAGE' },
    { k: 'users', t: '用户管理', ic: 'user', g: '系统设置', p: 'SYSTEM_ADMIN' },
    { k: 'roles', t: '角色权限', ic: 'role', g: '系统设置', p: 'SYSTEM_ADMIN' },
  ];
  const COMPONENTS = { dashboard: 'Dashboard', monitor: 'Monitor', track: 'Track', alarms: 'Alarms', vehicles: 'Vehicles', devices: 'Devices', fleets: 'Fleets',
    sessions: 'Sessions', commands: 'Commands', raw: 'Raw', notify: 'Notify', users: 'Users', roles: 'Roles' };
  const GROUPS = {
    '监控中心': { ic: 'monitor', d: '定位、轨迹与报警' },
    '车辆管理': { ic: 'truck', d: '车辆、车队与终端' },
    '指令调试': { ic: 'send', d: '指令下发与报文监视' },
    '系统设置': { ic: 'gear', d: '通知、用户与权限' },
  };

  const App = {
    components: { LoginView },
    setup() {
      const pages = computed(() => PAGES.filter(p => !p.p || has(p.p)).map(p => ({ ...p, c: Pages[COMPONENTS[p.k]] })).filter(p => p.c));
      const current = computed(() => pages.value.find(p => p.k === state.page) || pages.value[0]);
      const groups = computed(() => [...new Set(pages.value.map(p => p.g))].map(g => ({ g, ...GROUPS[g], items: pages.value.filter(p => p.g === g) })));
      const group = computed(() => current.value && groups.value.find(g => g.g === current.value.g));
      const readHash = () => { state.hash = location.hash; state.page = location.hash.slice(2).split('?')[0] || 'dashboard'; };
      readHash();
      window.addEventListener('hashchange', readHash);
      const nav = k => { location.hash = '/' + k; };
      async function logout() { await api('/auth/logout', { method: 'POST' }).catch(() => { }); state.me = null; }
      const pwd = reactive({ show: false, oldPassword: '', newPassword: '' });
      async function changePwd() {
        try { await api('/auth/password', { method: 'POST', body: { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword } }); ok('密码已修改'); pwd.show = false; } catch (e) { fail(e); }
      }
      const clock = ref('');
      const tick = () => {
        const d = nowCN(), p = n => String(n).padStart(2, '0');
        clock.value = d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
      };
      // 新报警提醒：轮询比上次新的报警，重要以上的弹窗
      let lastId = 0;
      const pendingCount = ref(0);
      async function pollAlarms() {
        if (!state.me || !has('ALARM_VIEW')) return;
        try {
          const list = await api('/alarms/latest?afterId=' + lastId);
          if (list.length) {
            if (lastId > 0) {
              list.filter(a => a.level >= 2).slice(0, 3).forEach(a => ElNotification({
                title: a.plate + ' ' + a.typeName, type: a.level === 3 ? 'error' : 'warning', duration: 6000,
                message: a.levelName + '报警 · ' + JT.fmt(a.startTime, true).slice(11) + ' · ' + a.speed + ' km/h',
                onClick: () => nav('alarms'),
              }));
              state.alarmTick++;
            }
            lastId = Math.max(lastId, ...list.map(a => a.id));
          }
          pendingCount.value = (await api('/alarms?status=PENDING&size=1')).total;
        } catch (e) { /* 忽略 */ }
      }
      tick();
      const t1 = setInterval(tick, 1000), t2 = setInterval(pollAlarms, 10000);
      onMounted(pollAlarms);
      onUnmounted(() => { clearInterval(t1); clearInterval(t2); });
      return { state, pages, current, groups, group, nav, logout, pwd, changePwd, has, clock, pendingCount, pollAlarms };
    },
    template: `<div v-if="state.ready">
      <login-view v-if="!state.me"/>
      <div v-else class="layout" @vue:mounted="pollAlarms">
        <header class="header">
          <div class="brand"><span class="logo-dot"><icon name="truck" :size="20"/></span><div>车辆智能监控平台<small>JT/T 808</small></div></div>
          <nav class="topnav">
            <a v-for="g in groups" :key="g.g" :class="{ on: g.g === current.g }" @click="nav(g.items[0].k)"><icon :name="g.ic" :size="17"/>{{ g.g }}</a>
          </nav>
          <div class="user">
            <a v-if="has('ALARM_VIEW')" class="pill" :class="{warn: pendingCount}" @click="nav('alarms')"><i></i>待处理报警 {{ pendingCount }}</a>
            <span class="clock"><icon name="clock" :size="15"/>{{ clock }}</span>
            <el-dropdown>
              <span class="who"><span class="avatar">{{ state.me.name.slice(-2) }}</span><span class="who-text">{{ state.me.name }}<small>{{ state.me.roleNames.join('、') }}</small></span></span>
              <template #dropdown><el-dropdown-menu><el-dropdown-item @click="pwd.show=true">修改密码</el-dropdown-item><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </div>
        </header>
        <div class="body" v-if="current">
          <aside class="side">
            <div class="side-head"><b><icon :name="group.ic" :size="19"/>{{ group.g }}</b><small>{{ group.d }}</small></div>
            <nav class="menu"><a v-for="p in group.items" :key="p.k" :class="{ on: p.k === current.k }" @click="nav(p.k)"><icon :name="p.ic"/><span>{{ p.t }}</span></a></nav>
            <div class="side-foot"><span>JT/T 808 · v1.0</span><span><a href="https://github.com/xiaowu-iot/jt-platform" target="_blank" rel="noopener">开源地址</a></span></div>
          </aside>
          <main class="main" :class="{ full: current.full }">
            <div class="page-head" v-if="!current.full"><div>
              <div class="crumb"><span>首页</span><span class="sep">/</span><span>{{ current.g }}</span><span class="sep">/</span><b>{{ current.t }}</b></div>
              <h2 class="page-title">{{ current.t }}</h2>
            </div></div>
            <div class="content"><component :is="current.c" :key="current.k + state.hash"/></div>
          </main>
        </div>
        <div v-else class="empty" style="padding-top:20vh">当前账号没有可用的功能，请联系管理员分配角色。</div>
        <el-dialog v-model="pwd.show" title="修改密码" width="380px">
          <el-form label-width="70px"><el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" show-password/></el-form-item><el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" show-password/></el-form-item></el-form>
          <template #footer><el-button @click="pwd.show=false">取消</el-button><el-button type="primary" @click="changePwd">保存</el-button></template>
        </el-dialog>
      </div>
    </div>`,
  };

  const app = createApp(App);  app.component('Icon', Icon);
  app.use(ElementPlus, { locale: window.ElementPlusLocaleZhCn });
  app.mount('#app');
  api('/auth/options').then(o => { state.options = o; }).catch(() => { })
    .then(() => api('/auth/me')).then(me => { state.me = me; }).catch(() => { }).finally(() => { state.ready = true; });
})();
