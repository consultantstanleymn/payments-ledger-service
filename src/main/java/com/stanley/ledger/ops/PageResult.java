package com.stanley.ledger.ops;

import java.util.List;

/** One page of a keyset paged list. nextCursor is null on the last page. */
public record PageResult<T>(List<T> items, String nextCursor) {}
