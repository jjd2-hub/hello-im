package com.him.implatform.exception;

import cn.hutool.json.JSONException;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.format.DateTimeParseException;
import java.util.List;

@ControllerAdvice
@ResponseBody
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 兜底捕获
     */
    @ExceptionHandler(value = Exception.class)
    public Result<?> handleException(Exception e) {
        if (e instanceof GlobalException ex) {
            if (!(ex.getCode().equals(ResultCode.INVALID_TOKEN.getCode()))) {
                log.error("全局异常捕获:msg:{}", ex.getMessage(), e);
            }
            return Result.error(ex.getCode(), ex.getMessage());
        } else if (e instanceof UndeclaredThrowableException) {
            GlobalException ex = (GlobalException) e.getCause();
            log.error("全局异常捕获:msg:{}", ex.getMessage(), e);
            return Result.error(ex.getCode(), ex.getMessage());
        } else {
            log.error("全局异常捕获:msg:{}", e.getMessage(), e);
            return Result.error(ResultCode.PROGRAM_ERROR);
        }
    }

    /**
     * 数据解析错误
     */
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("全局异常捕获:msg:{}", e.getMessage(), e);
        Throwable cause = e.getCause();
        if (cause instanceof JSONException) {
            cause = cause.getCause();
            if (cause instanceof DateTimeParseException) {
                return Result.error(ResultCode.PROGRAM_ERROR.getCode(), "日期格式不正确");
            }
            return Result.error(ResultCode.PROGRAM_ERROR.getCode(), "数据格式不正确");
        }
        return Result.error(ResultCode.PROGRAM_ERROR);
    }

    /**
     * 处理请求参数格式错误
     */
    @ExceptionHandler(value = {MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String msg = "系统繁忙，请稍后再试...";
        if (bindingResult.hasErrors()) {
            msg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            if (msg != null && msg.contains("NumberFormatException")) {
                msg = "参数类型错误！";
            }
        }
        return Result.error(ResultCode.PROGRAM_ERROR.getCode(), msg);
    }

    /**
     * 参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBindException(BindException e) {
        List<ObjectError> errors = e.getAllErrors();
        ObjectError error = errors.get(0);
        return Result.error(ResultCode.PROGRAM_ERROR.getCode(), error.getDefaultMessage());
    }
}
