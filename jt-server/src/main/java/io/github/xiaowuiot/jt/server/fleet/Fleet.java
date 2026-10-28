package io.github.xiaowuiot.jt.server.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 车队（分组），可以多级：公司 → 车队 → 班组。用户按车队限定能看哪些车。
 */
@Entity
@Table(name = "jt_fleet")
public class Fleet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** 上级车队，顶层为 null */
    private Long parentId;

    private int sort;

    @Column(length = 32)
    private String contact;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String remark;

    protected Fleet() {
    }

    public Fleet(String name, Long parentId, int sort) {
        this.name = name;
        this.parentId = parentId;
        this.sort = sort;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
