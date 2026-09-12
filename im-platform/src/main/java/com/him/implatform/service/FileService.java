package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.him.implatform.entity.FileInfo;
import com.him.implatform.vo.UploadImageVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService extends IService<FileInfo> {
    /**
     * 上传图片
     * @param file 图片文件
     * @param isPermanent 是否永久保存
     * @param thumbSize 图片尺寸
     * @return 图片信息
     */
    UploadImageVO uploadImage(MultipartFile file, Boolean isPermanent, Long thumbSize);

    /**
     * 上传文件
     * @param file 文件
     * @return 信息
     */
    String uploadFile(MultipartFile file);
}
