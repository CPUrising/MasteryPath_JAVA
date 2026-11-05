package com.masterypath.adapter.web;

import com.masterypath.storage.SettingsStore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设置分组读写（参考实现 {@code settings/store.py} + {@code api/app.py} 390–406 行）。
 *
 * <p>读写与归一全部委托 {@link SettingsStore}（落盘 {@code settings.json}）；
 * 本类只做 HTTP 形状与未知分组的 404 映射。
 *
 * <p>提供方探测（{@code /settings/providers/probe}）与 Task Model 属 {@code 0022}。
 */
@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsStore store;

    public SettingsController(SettingsStore store) {
        this.store = store;
    }

    @GetMapping
    public Map<String, Object> read() {
        return store.read();
    }

    @GetMapping("/{section}")
    public Object section(@PathVariable String section) {
        try {
            return store.section(section);
        } catch (SettingsStore.UnknownSection e) {
            throw new ApiException(404, e.getMessage());
        }
    }

    @PutMapping("/{section}")
    public Object writeSection(@PathVariable String section,
                               @RequestBody(required = false) Object payload) {
        try {
            return store.writeSection(section, payload);
        } catch (SettingsStore.UnknownSection e) {
            throw new ApiException(404, e.getMessage());
        }
    }
}
