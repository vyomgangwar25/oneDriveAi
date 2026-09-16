package com.example.demo.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One page of results.
 *
 * Spring's own Page is not serialized directly on purpose: its JSON carries the
 * whole Pageable and Sort structure, and that shape is an internal detail the
 * client would end up depending on.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> {

    private List<T> content;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;

    /** Saves the client from comparing page numbers to work this out. */
    private boolean hasNext;
}
