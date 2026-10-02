/**
 * Hợp đồng vào/ra của module courses. Facade/controller nhận/trả các record này nên package phải
 * được expose bằng {@code @NamedInterface}, nếu không {@code ApplicationModules.verify()} sẽ fail.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.courses.dto;
