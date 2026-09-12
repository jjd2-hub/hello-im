package com.him.implatform.controller;

import com.him.implatform.result.Result;
import com.him.implatform.service.FileService;
import com.him.implatform.vo.UploadImageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/upload")
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传图片", description = "上传图片,上传后返回原图和缩略图的url")
    @PostMapping("/image")
    public Result<UploadImageVO> uploadImage(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "true") Boolean isPermanent,
                                             @RequestParam(defaultValue = "50") Long thumbSize) {
        return Result.success(fileService.uploadImage(file,isPermanent,thumbSize));
    }

    @Operation(summary = "上传文件",description = "上传文件,上传后返回文件url")
    @PostMapping("/file")
    public Result<String> uploadFile(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadFile(file));
    }
}
