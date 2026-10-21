package io.github.xiaowuiot.jt.server.notify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotifyChannelRepository extends JpaRepository<NotifyChannel, Long> {

    List<NotifyChannel> findByEnabledTrue();
}
