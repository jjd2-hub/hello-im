package com.him.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.imcommon.util.FileUtil;
import com.him.imcommon.util.ImageUtil;
import com.him.implatform.config.props.MinioProperties;
import com.him.implatform.constant.Constant;
import com.him.implatform.entity.FileInfo;
import com.him.implatform.enums.FileType;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.FileInfoMapper;
import com.him.implatform.service.FileService;
import com.him.implatform.thirdparty.MinioService;
import com.him.implatform.vo.UploadImageVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements FileService {

    private final MinioService minioService;
    private final MinioProperties minioProps;

    @PostConstruct
    public void init() {
        if(!minioService.bucketExists(minioProps.getBucketName())){
            minioService.makeBucket(minioProps.getBucketName());
        }
        minioService.setBucketPublic(minioProps.getBucketName());
    }

    @Transactional
    @Override
    public UploadImageVO uploadImage(MultipartFile file, Boolean isPermanent, Long thumbSize) {
        try{
            checkImageFile(file);
            UploadImageVO vo=new UploadImageVO();
            BufferedImage bufferedImage= ImageIO.read(file.getInputStream());
            if(!Objects.isNull(bufferedImage)){
                vo.setWidth(bufferedImage.getWidth());
                vo.setHeight(bufferedImage.getHeight());
            }
            // 如果文件已存在，复用
            String md5= DigestUtils.md5DigestAsHex(file.getInputStream());
            FileInfo fileInfo=findByMd5(md5, FileType.IMAGE.code());
            if(!Objects.isNull(fileInfo)){
                fileInfo.setIsPermanent(isPermanent||fileInfo.getIsPermanent());
                fileInfo.setUploadTime(new Date());
                this.updateById(fileInfo);
                vo.setOriginUrl(fileInfo.getFilePath());
                vo.setThumbUrl(fileInfo.getCompressedPath());
                return vo;
            }
            // 没有就上传
            String fileName= minioService.upload(minioProps.getBucketName(), minioProps.getImagePath(),file);
            if(StringUtils.isEmpty(fileName)){
                throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"图片上传失败");
            }
            vo.setOriginUrl(generateUrl(FileType.IMAGE,fileName));
            // 大于50K文件需上传缩略图
            if(file.getSize()>thumbSize*1024){
                byte[] imageByte= ImageUtil.compressForScale(file.getBytes(),thumbSize);
                String thumbFileName= minioService.upload(minioProps.getBucketName(),minioProps.getImagePath(),
                        file.getOriginalFilename(),imageByte,file.getContentType());
                if(StringUtils.isEmpty(thumbFileName)){
                    throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"图片上传失败");
                }
                vo.setThumbUrl(generateUrl(FileType.IMAGE,thumbFileName));
                saveImageFileInfo(file,md5,vo.getOriginUrl(),vo.getThumbUrl(),isPermanent);
            }else{
                vo.setThumbUrl(generateUrl(FileType.IMAGE,fileName));
                // 缩略图不允许删除，所以原图也不允许删除
                saveImageFileInfo(file,md5,vo.getOriginUrl(),vo.getThumbUrl(),true);
            }
            log.info("图片上传成功,url:{}",vo.getOriginUrl());
            return vo;
        }catch (IOException e){
            log.error("上传图片失败,{}",e.getMessage(),e);
            throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"上传图片失败");
        }
    }

    @Override
    public String uploadFile(MultipartFile file) {
        try {
            checkFile(file);
            // 如果文件存在，复用
            String md5= DigestUtils.md5DigestAsHex(file.getInputStream());
            FileInfo fileInfo=findByMd5(md5, FileType.FILE.code());
            if(!Objects.isNull(fileInfo)){
                fileInfo.setUploadTime(new Date());
                this.updateById(fileInfo);
                return fileInfo.getFilePath();
            }
            // 不存在就上传
            String fileName= minioService.upload(minioProps.getBucketName(), minioProps.getFilePath(),file);
            if(StringUtils.isEmpty(fileName)){
                throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"文件上传失败");
            }
            String url=generateUrl(FileType.FILE,fileName);
            saveFileInfo(file,md5,url);
            log.info("文件上传成功,url:{}",url);
            return url;
        }catch (IOException e){
            log.error("上传文件失败,{}",e.getMessage(),e);
            throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"上传文件失败");
        }
    }

    private void saveImageFileInfo(MultipartFile file, String md5, String filePath, String compressedPath,
                                   Boolean isPermanent) throws IOException {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName(file.getOriginalFilename());
        fileInfo.setFileSize(file.getSize());
        fileInfo.setFileType(FileType.IMAGE.code());
        fileInfo.setFilePath(filePath);
        fileInfo.setCompressedPath(compressedPath);
        fileInfo.setMd5(md5);
        fileInfo.setIsPermanent(isPermanent);
        fileInfo.setUploadTime(new Date());
        this.save(fileInfo);
    }

    private void saveFileInfo(MultipartFile file, String md5, String filePath) throws IOException {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName(file.getOriginalFilename());
        fileInfo.setFileSize(file.getSize());
        fileInfo.setFileType(FileType.FILE.code());
        fileInfo.setFilePath(filePath);
        fileInfo.setMd5(md5);
        fileInfo.setIsPermanent(false);
        fileInfo.setUploadTime(new Date());
        this.save(fileInfo);
    }

    private String generateUrl(FileType fileType,String fileName){
        return StrUtil.join("/",minioProps.getDomain(),minioProps.getBucketName(),getBucketPath(fileType),fileName);
    }

    private String getBucketPath(FileType fileType) {
        return switch (fileType) {
            case FILE -> minioProps.getFilePath();
            case IMAGE -> minioProps.getImagePath();
            case VIDEO -> minioProps.getVideoPath();
        };
    }

    private FileInfo findByMd5(String md5,Integer fileType) {
        LambdaQueryWrapper<FileInfo> queryWrapper= Wrappers.lambdaQuery();
        queryWrapper.eq(FileInfo::getMd5,md5).eq(FileInfo::getFileType,fileType).last("limit 1");
        return this.getOne(queryWrapper);
    }

    private void checkFile(MultipartFile file){
        if(Objects.requireNonNull(file.getOriginalFilename()).length()> Constant.MAX_FILE_NAME_LENGTH){
            throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"文件长度不能超过"+Constant.MAX_FILE_SIZE);
        }
        if(file.getSize()>Constant.MAX_IMAGE_SIZE){
            throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"图片大小不符合要求");
        }
    }

    private void checkImageFile(MultipartFile file){
        checkFile(file);
        if(!FileUtil.isImage(file.getOriginalFilename())){
            throw new GlobalException(ResultCode.FILE_NOT_RIGHT.getCode(),"图片格式不符合要求");
        }
    }
}
