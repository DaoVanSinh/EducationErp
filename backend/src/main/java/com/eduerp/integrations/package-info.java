/**
 * Nơi chứa các adapter ra hệ thống ngoài (Redis, mail, storage, message broker…). Cũng như
 * {@code modules}, bản thân package này không phải một module — mỗi sub-package mới là một module.
 *
 * <p>Ranh giới cứng: ở đây không được xuất hiện tên một nghiệp vụ nào. Một class tên
 * {@code IdentitySomething} nằm trong {@code integrations} là dấu hiệu biên đã rò — tên nghiệp vụ
 * thuộc về module sở hữu nó, {@code integrations} chỉ cấp cơ chế.
 */
package com.eduerp.integrations;
