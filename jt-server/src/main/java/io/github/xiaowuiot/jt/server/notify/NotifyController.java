package io.github.xiaowuiot.jt.server.notify;

import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.common.PageResult;
import io.github.xiaowuiot.jt.server.config.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知渠道和发送记录。
 */
@RestController
@RequestMapping("/api/notify")
@Roles.On(Roles.PLATFORM)
@PreAuthorize("hasAuthority('PERM_NOTIFY_MANAGE')")
public class NotifyController {

    private final NotifyChannelRepository channels;
    private final NotifyRecordRepository records;
    private final NotifyService service;

    public NotifyController(NotifyChannelRepository channels, NotifyRecordRepository records, NotifyService service) {
        this.channels = channels;
        this.records = records;
        this.service = service;
    }

    public record ChannelForm(@NotBlank(message = "请填写名称") String name, NotifyChannel.Type type,
                              @NotBlank(message = "请填写地址") String url, String secret, Integer minLevel, Boolean enabled) {
    }

    /**
     * 密钥不回传给前端，只告诉前端配没配
     */
    public record ChannelView(Long id, String name, NotifyChannel.Type type, String url, boolean hasSecret, int minLevel,
                              boolean enabled) {
        static ChannelView of(NotifyChannel c) {
            return new ChannelView(c.getId(), c.getName(), c.getType(), c.getUrl(),
                    c.getSecret() != null && !c.getSecret().isBlank(), c.getMinLevel(), c.isEnabled());
        }
    }

    @GetMapping("/channels")
    public List<ChannelView> list() {
        return channels.findAll().stream().map(ChannelView::of).toList();
    }

    @PostMapping("/channels")
    public ChannelView create(@Valid @RequestBody ChannelForm f) {
        NotifyChannel c = new NotifyChannel(f.name().trim(), f.type() == null ? NotifyChannel.Type.DINGTALK : f.type(),
                f.url().trim(), blank(f.secret()), f.minLevel() == null ? 2 : f.minLevel());
        if (f.enabled() != null) {
            c.setEnabled(f.enabled());
        }
        return ChannelView.of(channels.save(c));
    }

    @PutMapping("/channels/{id}")
    public ChannelView update(@PathVariable Long id, @Valid @RequestBody ChannelForm f) {
        NotifyChannel c = channels.findById(id).orElseThrow(() -> ApiException.notFound("渠道不存在"));
        c.setName(f.name().trim());
        if (f.type() != null) {
            c.setType(f.type());
        }
        c.setUrl(f.url().trim());
        // 不填密钥表示不改
        if (f.secret() != null && !f.secret().isBlank()) {
            c.setSecret(f.secret().trim());
        }
        if (f.minLevel() != null) {
            c.setMinLevel(f.minLevel());
        }
        if (f.enabled() != null) {
            c.setEnabled(f.enabled());
        }
        return ChannelView.of(channels.save(c));
    }

    @DeleteMapping("/channels/{id}")
    public Map<String, Boolean> delete(@PathVariable Long id) {
        channels.deleteById(id);
        return Map.of("ok", true);
    }

    @PostMapping("/channels/{id}/test")
    public NotifyRecord test(@PathVariable Long id) {
        return service.test(channels.findById(id).orElseThrow(() -> ApiException.notFound("渠道不存在")));
    }

    @GetMapping("/records")
    public PageResult<NotifyRecord> records(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(records.findAllByOrderByIdDesc(PageRequest.of(page, Math.min(size, 100))), r -> r);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
