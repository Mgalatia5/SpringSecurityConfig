package com.example.demo.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostRequest(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String content) {
}
