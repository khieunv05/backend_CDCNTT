package com.example.english_app_cdcntt.dto;

/**
 * Success body of POST/PUT word and POST phrase (§4:174): {@code {"message":"...","data":{...}}}.
 * The message stays outside the data DTO on purpose — GET and auth responses are not wrapped.
 */
public record SuccessResponse<T>(String message, T data) {
}
