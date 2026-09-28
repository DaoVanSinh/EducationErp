/**
 * Adapter phía identity cho cổng mà access tự khai báo
 * ({@link com.eduerp.modules.access.AccountExistenceCheck}) — identity implement, access chỉ biết
 * interface của chính nó. Đây là chiều import DUY NHẤT giữa hai module: identity → access; access
 * không bao giờ import identity.
 */
package com.eduerp.modules.identity.internal.access;
