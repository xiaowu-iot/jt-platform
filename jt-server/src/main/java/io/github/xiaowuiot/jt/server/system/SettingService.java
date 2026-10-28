package io.github.xiaowuiot.jt.server.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaowuiot.jt.server.config.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 页面上改的平台设置，存 sys_setting，读的时候走内存缓存（到点判定每个位置点都要读，不能每次查库）
 */
@Service
@Roles.On(Roles.PLATFORM)
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);

    private final SettingRepository repo;
    private final ObjectMapper json;
    private final Map<String, Optional<Object>> cache = new ConcurrentHashMap<>();

    public SettingService(SettingRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    /** 读一个设置；没存过或者解析不了返回空 */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        return (Optional<T>) cache.computeIfAbsent(key, k -> {
            try {
                Setting s = repo == null ? null : repo.findById(k).orElse(null);
                return s == null || s.getValue() == null ? Optional.empty() : Optional.of(json.readValue(s.getValue(), type));
            } catch (Exception e) {
                log.warn("设置 {} 解析失败，按默认值用：{}", k, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public void save(String key, Object value, String operator) {
        try {
            Setting s = repo.findById(key).orElseGet(() -> new Setting(key));
            s.setValue(json.writeValueAsString(value));
            s.setUpdatedAt(LocalDateTime.now());
            s.setUpdatedBy(operator);
            repo.save(s);
        } catch (Exception e) {
            throw new IllegalStateException("保存设置失败：" + e.getMessage(), e);
        }
        cache.remove(key);
    }

    public Optional<Setting> raw(String key) {
        return repo.findById(key);
    }
}
