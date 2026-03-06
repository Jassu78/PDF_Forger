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
import dev.pdfforge.feature.conversion.ConversionScreen
import dev.pdfforge.feature.conversion.ConversionViewModel
import dev.pdfforge.feature.home.HomeScreen
import dev.pdfforge.feature.home.HomeViewModel
import dev.pdfforge.feature.merge_split.MergePdfScreen
import dev.pdfforge.feature.merge_split.MergePdfViewModel
import dev.pdfforge.feature.merge_split.ReorderPagesScreen
import dev.pdfforge.feature.merge_split.ReorderPagesViewModel
import dev.pdfforge.feature.merge_split.SplitPdfScreen
import dev.pdfforge.feature.merge_split.SplitPdfViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.pdfforge.common.ui.components.DocumentResultScreen
import dev.pdfforge.common.ui.components.PdfResultScreen
import dev.pdfforge.feature.conversion.OutputFormat
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
                                onToolClick = { toolId: String ->
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
                                onBackClick = { navController.popBackStack() },
                                onPdfCreated = { uri ->
                                    navController.navigate("pdf_result/${android.net.Uri.encode(uri.toString())}")
                                }
                            )
                        }

                        composable(
                            route = Screen.PdfResult.route,
                            arguments = listOf(navArgument("pdfUri") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val pdfUri = backStackEntry.arguments?.getString("pdfUri")
                            PdfResultScreen(
                                pdfUriString = pdfUri,
                                title = "PDF Created",
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.MergePdf.route) {
                            val viewModel: MergePdfViewModel = hiltViewModel()
                            MergePdfScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onPdfCreated = { uri ->
                                    navController.navigate("pdf_result/${android.net.Uri.encode(uri.toString())}")
                                }
                            )
                        }

                        composable(Screen.CompressPdf.route) {
                            val viewModel: CompressionViewModel = hiltViewModel()
                            CompressionScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onPdfCreated = { uri ->
                                    navController.navigate("pdf_result/${android.net.Uri.encode(uri.toString())}")
                                }
                            )
                        }

                        composable(Screen.ConvertPdf.route) {
                            val viewModel: ConversionViewModel = hiltViewModel()
                            ConversionScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onDocumentCreated = { uri, format ->
                                    val mimeKey = when (format) {
                                        OutputFormat.DOCX -> "docx"
                                        OutputFormat.PPTX -> "pptx"
                                        OutputFormat.TXT -> "txt"
                                        OutputFormat.IMAGES -> "zip"
                                        OutputFormat.MD -> "md"
                                    }
                                    navController.navigate("document_result/${android.net.Uri.encode(uri.toString())}/$mimeKey")
                                }
                            )
                        }

                        composable(
                            route = Screen.DocumentResult.route,
                            arguments = listOf(
                                navArgument("documentUri") { type = NavType.StringType },
                                navArgument("mimeKey") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val documentUri = backStackEntry.arguments?.getString("documentUri")
                            val mimeKey = backStackEntry.arguments?.getString("mimeKey") ?: "docx"
                            DocumentResultScreen(
                                documentUriString = documentUri,
                                mimeKey = mimeKey,
                                title = "Conversion Complete",
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.SplitPdf.route) {
                            val viewModel: SplitPdfViewModel = hiltViewModel()
                            SplitPdfScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onPdfCreated = { uri ->
                                    navController.navigate("pdf_result/${android.net.Uri.encode(uri.toString())}")
                                }
                            )
                        }
                        composable(Screen.ReorderPages.route) {
                            val viewModel: ReorderPagesViewModel = hiltViewModel()
                            ReorderPagesScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onPdfCreated = { uri ->
                                    navController.navigate("pdf_result/${android.net.Uri.encode(uri.toString())}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
