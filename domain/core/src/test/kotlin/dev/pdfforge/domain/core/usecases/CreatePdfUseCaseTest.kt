package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.ImageToPdfParams
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import dev.pdfforge.domain.models.ErrorCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CreatePdfUseCaseTest {

    private val tool: ImageToPdfTool = mockk()
    private val useCase = CreatePdfUseCase(tool)

    @Test
    fun `invoke should return success when tool executes successfully`() = runTest {
        // Arrange
        val params = ImageToPdfParams(listOf(mockk()), "output.pdf")
        val expectedUri = mockk<Uri>()
        every { tool.validate(params) } returns ValidationResult(true)
        coEvery { tool.execute(params) } returns OperationResult.Success(expectedUri)

        // Act
        val result = useCase(params)

        // Assert
        assertTrue(result is OperationResult.Success)
        assertEquals(expectedUri, (result as OperationResult.Success).data)
    }

    @Test
    fun `invoke should return error when tool execution fails`() = runTest {
        // Arrange
        val params = ImageToPdfParams(listOf(mockk()), "output.pdf")
        every { tool.validate(params) } returns ValidationResult(true)
        coEvery { tool.execute(params) } returns OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR)

        // Act
        val result = useCase(params)

        // Assert
        assertTrue(result is OperationResult.Error)
        assertEquals(ErrorCode.ENGINE_INTERNAL_ERROR, (result as OperationResult.Error).code)
    }
}
