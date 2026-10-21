/* 系统设置：通知配置、用户、角色 */
(function () {
  const { ref, reactive, computed, onMounted } = Vue;
  const { api, fail, ok, fmt, has, qs, LEVEL, ElMessageBox } = JT;

  // ===================== 通知配置 =====================
  const Notify = {
    setup() {
      const channels = ref([]);
      const records = ref({ items: [], total: 0 });
      const page = ref(0);
      const dlg = reactive({ show: false, id: null, f: {} });
      async function load() {
        try { channels.value = await api('/notify/channels'); records.value = await api('/notify/records?' + qs({ page: page.value, size: 10 })); } catch (e) { fail(e); }
      }
      onMounted(load);
      function edit(row) {
        dlg.id = row ? row.id : null;
        dlg.f = row ? { name: row.name, type: row.type, url: row.url, secret: '', minLevel: row.minLevel, enabled: row.enabled, hasSecret: row.hasSecret }
          : { type: 'DINGTALK', minLevel: 2, enabled: true };
        dlg.show = true;
      }
      async function save() {
        try { await api('/notify/channels' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      async function test(row) {
        try { const r = await api('/notify/channels/' + row.id + '/test', { method: 'POST' }); r.success ? ok('发送成功') : fail(new Error('发送失败：' + r.error)); load(); } catch (e) { fail(e); }
      }
      async function del(row) {
        try { await ElMessageBox.confirm('删除渠道 ' + row.name + '？', '确认', { type: 'warning' }); } catch (e) { return; }
        try { await api('/notify/channels/' + row.id, { method: 'DELETE' }); load(); } catch (e) { fail(e); }
      }
      return { channels, records, page, dlg, load, edit, save, test, del, fmt, LEVEL };
    },
    template: `<div>
      <div class="panel">
        <div class="panel-title">通知渠道<el-button size="small" type="primary" @click="edit(null)">新增渠道</el-button></div>
        <div class="muted" style="margin-bottom:10px">产生新报警时，级别不低于渠道设置的，推送到钉钉群机器人或者你们自己系统的 Webhook。</div>
        <el-table :data="channels" size="small">
          <el-table-column prop="name" label="名称" width="200"/>
          <el-table-column label="类型" width="110"><template #default="{row}">{{ row.type==='DINGTALK' ? '钉钉机器人' : 'Webhook' }}</template></el-table-column>
          <el-table-column prop="url" label="地址" min-width="240" show-overflow-tooltip/>
          <el-table-column label="最低级别" width="90"><template #default="{row}">{{ LEVEL[row.minLevel].t }}</template></el-table-column>
          <el-table-column label="加签" width="70"><template #default="{row}">{{ row.hasSecret ? '已配置' : '—' }}</template></el-table-column>
          <el-table-column label="启用" width="70"><template #default="{row}"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="180"><template #default="{row}"><el-button link type="primary" @click="test(row)">发测试消息</el-button><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" @click="del(row)">删除</el-button></template></el-table-column>
        </el-table>
      </div>
      <div class="panel">
        <div class="panel-title">发送记录</div>
        <el-table :data="records.items" size="small">
          <el-table-column label="时间" width="150"><template #default="{row}">{{ fmt(row.createdAt, true) }}</template></el-table-column>
          <el-table-column prop="channelName" label="渠道" width="180"/>
          <el-table-column prop="title" label="标题" min-width="200"/>
          <el-table-column label="结果" width="80"><template #default="{row}"><el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
          <el-table-column prop="error" label="错误" min-width="160" show-overflow-tooltip/>
        </el-table>
        <div class="pager"><el-pagination layout="total, prev, pager, next" :total="records.total" :page-size="10" :current-page="page + 1" @current-change="p => { page = p - 1; load(); }"/></div>
      </div>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑渠道' : '新增渠道'" width="520px">
        <el-form label-width="90px">
          <el-form-item label="名称" required><el-input v-model="dlg.f.name"/></el-form-item>
          <el-form-item label="类型"><el-radio-group v-model="dlg.f.type"><el-radio value="DINGTALK">钉钉机器人</el-radio><el-radio value="WEBHOOK">Webhook</el-radio></el-radio-group></el-form-item>
          <el-form-item label="地址" required><el-input v-model="dlg.f.url" :placeholder="dlg.f.type==='DINGTALK' ? 'https://oapi.dingtalk.com/robot/send?access_token=...' : 'https://...'"/></el-form-item>
          <el-form-item v-if="dlg.f.type==='DINGTALK'" label="加签密钥"><el-input v-model="dlg.f.secret" :placeholder="dlg.f.hasSecret ? '已配置，不改就留空' : 'SEC 开头'" show-password/></el-form-item>
          <el-form-item label="最低级别"><el-select v-model="dlg.f.minLevel"><el-option v-for="(v,k) in LEVEL" :key="k" :value="Number(k)" :label="v.t"/></el-select></el-form-item>
          <el-form-item label="启用"><el-switch v-model="dlg.f.enabled"/></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ===================== 用户 =====================
  const Users = {
    setup() {
      const list = ref([]);
      const roles = ref([]);
      const fleets = ref([]);
      const tree = computed(() => JT.Pages.toTree(fleets.value));
      const dlg = reactive({ show: false, id: null, f: {} });
      async function load() {
        try { list.value = await api('/users'); roles.value = await api('/roles'); fleets.value = await api('/fleets'); } catch (e) { fail(e); }
      }
      onMounted(load);
      function edit(row) {
        dlg.id = row ? row.id : null;
        dlg.f = row ? { username: row.username, name: row.name, phone: row.phone, enabled: row.enabled, roleIds: row.roleIds, fleetIds: row.fleetIds, password: '' }
          : { enabled: true, roleIds: [], fleetIds: [], password: '' };
        dlg.show = true;
      }
      async function save() {
        try { await api('/users' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      const fleetNames = ids => ids.length ? ids.map(id => (fleets.value.find(f => f.id === id) || {}).name).filter(Boolean).join('、') : '全部车队';
      return { list, roles, tree, dlg, edit, save, fmt, fleetNames };
    },
    template: `<div class="panel">
      <div class="toolbar"><span class="grow"></span><el-button type="primary" @click="edit(null)">新增用户</el-button></div>
      <el-table :data="list" size="small">
        <el-table-column prop="username" label="用户名" width="120"/><el-table-column prop="name" label="姓名" width="120"/>
        <el-table-column prop="phone" label="手机" width="130"/>
        <el-table-column label="角色" min-width="160"><template #default="{row}">{{ row.roleNames.join('、') }}</template></el-table-column>
        <el-table-column label="负责车队" min-width="160"><template #default="{row}">{{ fleetNames(row.fleetIds) }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{row}"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '正常' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="创建时间" width="150"><template #default="{row}">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button></template></el-table-column>
      </el-table>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑用户' : '新增用户'" width="480px">
        <el-form label-width="80px">
          <el-form-item label="用户名" required><el-input v-model="dlg.f.username"/></el-form-item>
          <el-form-item label="姓名" required><el-input v-model="dlg.f.name"/></el-form-item>
          <el-form-item label="手机"><el-input v-model="dlg.f.phone"/></el-form-item>
          <el-form-item :label="dlg.id ? '新密码' : '密码'"><el-input v-model="dlg.f.password" type="password" show-password :placeholder="dlg.id ? '不改就留空' : '至少 6 位'"/></el-form-item>
          <el-form-item label="角色"><el-select v-model="dlg.f.roleIds" multiple style="width:100%"><el-option v-for="r in roles" :key="r.id" :value="r.id" :label="r.name"/></el-select></el-form-item>
          <el-form-item label="负责车队"><el-tree-select v-model="dlg.f.fleetIds" :data="tree" :props="{label:'name', value:'id'}" multiple check-strictly show-checkbox placeholder="不选表示全部车队" style="width:100%"/></el-form-item>
          <el-form-item label="启用"><el-switch v-model="dlg.f.enabled"/></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ===================== 角色 =====================
  const Roles = {
    setup() {
      const list = ref([]);
      const perms = ref([]);
      const dlg = reactive({ show: false, id: null, f: {} });
      async function load() { try { list.value = await api('/roles'); perms.value = await api('/roles/permissions'); } catch (e) { fail(e); } }
      onMounted(load);
      function edit(row) {
        dlg.id = row ? row.id : null;
        dlg.f = row ? { name: row.name, remark: row.remark, permissions: [...row.permissions] } : { permissions: [] };
        dlg.show = true;
      }
      async function save() {
        try { await api('/roles' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      async function del(row) {
        try { await ElMessageBox.confirm('删除角色 ' + row.name + '？', '确认', { type: 'warning' }); } catch (e) { return; }
        try { await api('/roles/' + row.id, { method: 'DELETE' }); load(); } catch (e) { fail(e); }
      }
      const label = c => (perms.value.find(p => p.code === c) || {}).label || c;
      return { list, perms, dlg, edit, save, del, label };
    },
    template: `<div class="panel">
      <div class="toolbar"><span class="muted">角色由权限点自由组合，内置角色不能删除。</span><span class="grow"></span><el-button type="primary" @click="edit(null)">新增角色</el-button></div>
      <el-table :data="list" size="small">
        <el-table-column prop="name" label="角色" width="140"/><el-table-column prop="remark" label="说明" width="220"/>
        <el-table-column label="权限" min-width="300"><template #default="{row}"><el-tag v-for="p in row.permissions" :key="p" size="small" class="tag-gap">{{ label(p) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button v-if="!row.builtin" link type="danger" @click="del(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑角色' : '新增角色'" width="560px">
        <el-form label-width="70px">
          <el-form-item label="名称" required><el-input v-model="dlg.f.name"/></el-form-item>
          <el-form-item label="说明"><el-input v-model="dlg.f.remark"/></el-form-item>
          <el-form-item label="权限"><el-checkbox-group v-model="dlg.f.permissions" class="perm-grid"><el-checkbox v-for="p in perms" :key="p.code" :value="p.code">{{ p.label }}</el-checkbox></el-checkbox-group></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ===================== 登录记录（系统管理员） =====================
  const LoginLogs = {
    setup() {
      const q = reactive({ keyword: '', action: '', range: null, page: 0, size: 20 });
      const data = ref({ items: [], total: 0 });
      const loading = ref(false);
      async function load() {
        loading.value = true;
        try {
          const p = { keyword: q.keyword, action: q.action, page: q.page, size: q.size };
          if (q.range) { p.from = q.range[0]; p.to = q.range[1]; }
          data.value = await api('/audit/logins?' + qs(p));
        } catch (e) { fail(e); } finally { loading.value = false; }
      }
      function search() { q.page = 0; load(); }
      onMounted(load);
      const TAG = { LOGIN: 'success', LOGIN_FAILED: 'danger', LOGOUT: 'info' };
      return { q, data, loading, load, search, fmt, TAG };
    },
    template: `<div class="panel">
      <div class="muted" style="margin-bottom:10px">每次登录、登录失败和退出都会记一条：账号、时间、来源 IP、从哪个端登录、浏览器。记录保留 180 天。</div>
      <div class="toolbar">
        <el-input v-model="q.keyword" placeholder="账号、姓名或 IP" clearable style="width:200px" @keyup.enter="search"/>
        <el-select v-model="q.action" placeholder="全部类型" clearable style="width:130px"><el-option label="登录成功" value="LOGIN"/><el-option label="登录失败" value="LOGIN_FAILED"/><el-option label="退出登录" value="LOGOUT"/></el-select>
        <el-date-picker v-model="q.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:260px"/>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
      <el-table :data="data.items" size="small" v-loading="loading">
        <el-table-column label="时间" width="160"><template #default="{row}">{{ fmt(row.createdAt, true) }}</template></el-table-column>
        <el-table-column label="账号" width="160"><template #default="{row}">{{ row.username }}<span v-if="row.name" class="muted"> · {{ row.name }}</span></template></el-table-column>
        <el-table-column label="类型" width="100"><template #default="{row}"><el-tag size="small" :type="TAG[row.action]">{{ row.actionLabel }}</el-tag></template></el-table-column>
        <el-table-column prop="message" label="说明" width="120"/>
        <el-table-column prop="ip" label="IP" width="140"/>
        <el-table-column prop="client" label="端" width="90"/>
        <el-table-column prop="browser" label="浏览器" min-width="140"/>
      </el-table>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="q.size" :current-page="q.page + 1" @current-change="p => { q.page = p - 1; load(); }"/></div>
    </div>`,
  };

  Object.assign(JT.Pages, { Notify, Users, Roles, LoginLogs });
})();
