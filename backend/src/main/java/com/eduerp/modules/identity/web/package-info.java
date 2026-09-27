/**
 * Adapter HTTP của module identity: controller mỏng (rule #8 — không chứa nghiệp vụ), cookie phiên,
 * filter xác thực và filter chain. Security config nằm ở đây thay vì {@code core/security} vì nó
 * tham chiếu use case và permission evaluator của identity — {@code core} không được biết nghiệp vụ (rule #4).
 */
package com.eduerp.modules.identity.web;
