package dev.pdfforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.pdfforge.common.ui.navigation.Screen
import dev.pdfforge.common.ui.theme.PdfForgeTheme
import dev.pdfforge.feature.compression.CompressionScreen
import dev.pdfforge.feature.compression.CompressionViewModel
import dev.pdfforge.feature.home.HomeScreen
import dev.pdfforge.feature.home.HomeViewModel
import dev.pdfforge.feature.merge_split.MergePdfScreen
import dev.pdfforge.feature.merge_split.MergePdfViewModel
import dev.pdfforge.feature.pdf_creation.ImageToPdfScreen
import dev.pdfforge.feature.pdf_creation.ImageToPdfViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PdfForgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            val viewModel: HomeViewModel = hiltViewModel()
                            HomeScreen(
                                viewModel = viewModel,
                                onToolClick = { toolId ->
                                    when (toolId) {
                                        "image_to_pdf" -> navController.navigate(Screen.ImageToPdf.route)
                                        "merge_pdf" -> navController.navigate(Screen.MergePdf.route)
                                        "compress_pdf" -> navController.navigate(Screen.CompressPdf.route)
                                        "convert_pdf" -> navController.navigate(Screen.ConvertPdf.route)
                                        "split_pdf" -> navController.navigate(Screen.SplitPdf.route)
                                        "reorder_pages" -> navController.navigate(Screen.ReorderPages.route)
                                    }
                                }
                            )
                        }
                        
                        composable(Screen.ImageToPdf.route) {
                            val viewModel: ImageToPdfViewModel = hiltViewModel()
                            ImageToPdfScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.MergePdf.route) {
                            val viewModel: MergePdfViewModel = hiltViewModel()
                            MergePdfScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.CompressPdf.route) {
                            val viewModel: CompressionViewModel = hiltViewModel()
                            CompressionScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        
                        // Remaining placeholders
                        composable(Screen.ConvertPdf.route) { /* TODO */ }
                        composable(Screen.SplitPdf.route) { /* TODO */ }
                        composable(Screen.ReorderPages.route) { /* TODO */ }
                    }
                }
            }
        }
    }
}
