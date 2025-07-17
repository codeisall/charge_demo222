package com.pdsu.charge_palteform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
@Tag(name = "第三方平台推送处理", description = "接收和处理第三方平台主动推送的信息")
public class NotificationController {



}
