/**
 * Adapter phía organization cho cổng mà access tự khai báo
 * ({@link com.eduerp.modules.access.BranchCatalog}) — organization implement, access chỉ biết
 * interface của chính nó. Đây là chiều import DUY NHẤT giữa hai module: organization → access
 * (cũng là chiều mà {@code web.BranchAdminController} dùng cho {@code AccessConstants}); access
 * không bao giờ import organization.
 */
package com.eduerp.modules.organization.internal.access;
