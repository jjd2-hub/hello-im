package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.him.implatform.config.props.MinioProperties;
import com.him.implatform.entity.FileInfo;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.mapper.FileInfoMapper;
import com.him.implatform.service.impl.FileServiceImpl;
import com.him.implatform.thirdparty.MinioService;
import com.him.implatform.vo.UploadImageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileServiceImpl 单元测试")
class FileServiceImplTest {

    @Mock
    private MinioService minioService;

    @Mock
    private MinioProperties minioProps;

    @Mock
    private FileInfoMapper fileInfoMapper;

    @InjectMocks
    private FileServiceImpl fileService;

    private MockMultipartFile imageFile;
    private MockMultipartFile largeImageFile;
    private MockMultipartFile textFile;

    @BeforeEach
    void setUp() throws Exception {
        // 注入 baseMapper
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(fileService, fileInfoMapper);

        // mock 配置
        when(minioProps.getBucketName()).thenReturn("box-im");
        when(minioProps.getImagePath()).thenReturn("image");
        when(minioProps.getFilePath()).thenReturn("file");
        when(minioProps.getVideoPath()).thenReturn("video");
        when(minioProps.getDomain()).thenReturn("http://127.0.0.1:9000");

        // 100x100 的小图片（几KB）
        imageFile = createMockImageFile();

        // 用随机噪点生成的图片，压缩后很大，确保超过 thumbSize
        largeImageFile = createNoisyImageFile();

        // 普通文本文件
        textFile = new MockMultipartFile(
                "file", "test.txt", "text/plain",
                "hello world".getBytes(StandardCharsets.UTF_8));
    }

    // ==================== init 初始化测试 ====================

    @Test
    @DisplayName("init - bucket不存在时创建bucket并设置公开")
    void init_bucketNotExists_shouldCreateAndSetPublic() {
        when(minioService.bucketExists("box-im")).thenReturn(false);

        fileService.init();

        verify(minioService, times(1)).makeBucket("box-im");
        verify(minioService, times(1)).setBucketPublic("box-im");
    }

    @Test
    @DisplayName("init - bucket已存在时只设置公开")
    void init_bucketExists_shouldOnlySetPublic() {
        when(minioService.bucketExists("box-im")).thenReturn(true);

        fileService.init();

        verify(minioService, never()).makeBucket(anyString());
        verify(minioService, times(1)).setBucketPublic("box-im");
    }

    // ==================== uploadImage 图片上传测试 ====================

    @Test
    @DisplayName("uploadImage - 正常上传新图片（小于thumbSize）")
    void uploadImage_newSmallImage_shouldUploadOnce() {
        when(fileInfoMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(minioService.upload(anyString(), anyString(), any())).thenReturn("20260912/abc.png");

        // thumbSize=50，图片只有几KB，不走缩略图分支
        UploadImageVO result = fileService.uploadImage(imageFile, false, 50L);

        assertNotNull(result.getOriginUrl());
        assertNotNull(result.getThumbUrl());
        assertEquals(100, result.getWidth());
        assertEquals(100, result.getHeight());
        // 只上传了一次（原图），缩略图=原图
        verify(minioService, times(1)).upload(anyString(), anyString(), any(MultipartFile.class));
    }

    @Test
    @DisplayName("uploadImage - 正常上传新图片（大于thumbSize）需要缩略图")
    void uploadImage_newLargeImage_shouldUploadWithThumbnail() {
        when(fileInfoMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(minioService.upload(anyString(), anyString(), any())).thenReturn("20260912/large.png");
        when(minioService.upload(anyString(), anyString(), anyString(), any(byte[].class), anyString()))
                .thenReturn("20260912/thumb.png");

        // thumbSize=1，largeImageFile 文件大小 > 1KB，走缩略图分支
        UploadImageVO result = fileService.uploadImage(largeImageFile, true, 1L);

        assertNotNull(result.getOriginUrl());
        assertNotNull(result.getThumbUrl());
        // 上传了两次：原图 + 缩略图
        verify(minioService, times(1)).upload(anyString(), anyString(), any(MultipartFile.class));
        verify(minioService, times(1)).upload(anyString(), anyString(), anyString(), any(byte[].class), anyString());
    }

    @Test
    @DisplayName("uploadImage - 图片已存在时复用")
    void uploadImage_existingImage_shouldReuse() {
        FileInfo existingFile = new FileInfo();
        existingFile.setFilePath("http://127.0.0.1:9000/box-im/image/existing.png");
        existingFile.setCompressedPath("http://127.0.0.1:9000/box-im/image/existing_thumb.png");
        existingFile.setIsPermanent(false);
        existingFile.setUploadTime(new Date());

        when(fileInfoMapper.selectOne(any(), anyBoolean())).thenReturn(existingFile);

        UploadImageVO result = fileService.uploadImage(imageFile, true, 50L);

        assertEquals("http://127.0.0.1:9000/box-im/image/existing.png", result.getOriginUrl());
        verify(minioService, never()).upload(anyString(), anyString(), any(MultipartFile.class));
    }

    @Test
    @DisplayName("uploadImage - 非图片文件应抛异常")
    void uploadImage_notImageFile_shouldThrowException() {
        assertThrows(GlobalException.class,
                () -> fileService.uploadImage(textFile, false, 50L));
    }

    @Test
    @DisplayName("uploadImage - MinIO上传失败应抛异常")
    void uploadImage_minioUploadFail_shouldThrowException() {
        when(fileInfoMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(minioService.upload(anyString(), anyString(), any())).thenReturn("");

        assertThrows(GlobalException.class,
                () -> fileService.uploadImage(imageFile, false, 50L));
    }

    // ==================== uploadFile 文件上传测试 ====================

    @Test
    @DisplayName("uploadFile - 正常上传新文件")
    void uploadFile_newFile_shouldUpload() {
        when(fileInfoMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(minioService.upload(anyString(), anyString(), any())).thenReturn("20260912/test.txt");

        String url = fileService.uploadFile(textFile);

        assertNotNull(url);
        verify(fileInfoMapper, times(1)).insert(any(FileInfo.class));
    }

    @Test
    @DisplayName("uploadFile - 文件已存在时复用")
    void uploadFile_existingFile_shouldReuse() {
        FileInfo existingFile = new FileInfo();
        existingFile.setFilePath("http://127.0.0.1:9000/box-im/file/existing.txt");
        existingFile.setUploadTime(new Date());

        when(fileInfoMapper.selectOne(any(), anyBoolean())).thenReturn(existingFile);

        String url = fileService.uploadFile(textFile);

        assertEquals("http://127.0.0.1:9000/box-im/file/existing.txt", url);
        verify(minioService, never()).upload(anyString(), anyString(), any(MultipartFile.class));
    }

    @Test
    @DisplayName("uploadFile - MinIO上传失败应抛异常")
    void uploadFile_minioUploadFail_shouldThrowException() {
        when(fileInfoMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(minioService.upload(anyString(), anyString(), any())).thenReturn(null);

        assertThrows(GlobalException.class,
                () -> fileService.uploadFile(textFile));
    }

    // ==================== 辅助方法 ====================

    /** 生成纯色小图片（压缩后很小） */
    private MockMultipartFile createMockImageFile() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        return toMockMultipartFile("test.png", image);
    }

    /** 生成随机噪点图片（压缩后很大） */
    private MockMultipartFile createNoisyImageFile() {
        BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 500; x++) {
            for (int y = 0; y < 500; y++) {
                image.setRGB(x, y, new java.util.Random().nextInt());
            }
        }
        return toMockMultipartFile("large.png", image);
    }

    private MockMultipartFile toMockMultipartFile(String filename, BufferedImage image) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", stream);
            return new MockMultipartFile("file", filename, "image/png", stream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
