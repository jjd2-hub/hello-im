package com.him.implatform.result;

import com.him.implatform.enums.ResultCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "统一返回结果")
@Data
public class Result<T> {
    @Schema(description = "状态码")
    private int code;
    @Schema(description = "消息")
    private String message;
    @Schema(description = "数据")
    private T data;

    /**
     * 成功(200,"成功")
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = ResultCode.SUCCESS.getCode();
        result.message = ResultCode.SUCCESS.getMsg();
        return result;
    }

    /**
     * 成功(200,"成功",data)
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<T>();
        result.code = ResultCode.SUCCESS.getCode();
        result.message = ResultCode.SUCCESS.getMsg();
        result.data = data;
        return result;
    }

    /**
     * 失败(code,msg)
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        Result<T> result = new Result<T>();
        result.code = resultCode.getCode();
        result.message = resultCode.getMsg();
        return result;
    }

    /**
     * 失败(code,msg)
     */
    public static <T> Result<T> error(Integer code,String message){
        Result<T> result = new Result<T>();
        result.code = code;
        result.message = message;
        return result;
    }
}
