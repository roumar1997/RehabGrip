package com.rehabgrip.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.designsystem.components.RgPrimaryButton
import com.rehabgrip.core.designsystem.illustrations.Brand
import com.rehabgrip.core.designsystem.theme.RgColors

/** First launch: welcome, then ask for the user's name (no login, local only). */
@Composable
fun OnboardingScreen(onNameConfirmed: (String) -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF4F8FE), Color(0xFFDCE9FA), Color(0xFFB9D3F3))),
        ),
    ) {
        Image(
            Brand.Ridge, null,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f).align(Alignment.BottomCenter),
            contentScale = ContentScale.FillWidth,
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Image(Brand.Mountain, null, Modifier.fillMaxWidth(0.62f).aspectRatio(200f / 110f))
            Spacer(Modifier.height(12.dp))
            Text("RehabGrip", style = MaterialTheme.typography.displayMedium, color = RgColors.Navy)
            Text(
                "Tu rehabilitación\nen buenas manos",
                style = MaterialTheme.typography.titleLarge,
                color = RgColors.Navy.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            if (step == 0) WelcomeStep { step = 1 } else NameStep(name, { name = it }) {
                if (name.isNotBlank()) onNameConfirmed(name.trim())
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            "Registra, controla y visualiza\ntu progreso de fuerza de agarre",
            style = MaterialTheme.typography.bodyLarge,
            color = RgColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        RgPrimaryButton("Comenzar", onStart, Modifier.widthIn(max = 420.dp), icon = Icons.AutoMirrored.Rounded.ArrowForward)
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onConfirm: () -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("¿Cómo te llamas?", style = MaterialTheme.typography.titleLarge, color = RgColors.Navy)
        Text(
            "Solo se guarda en este móvil. No hace falta cuenta.",
            style = MaterialTheme.typography.bodyMedium,
            color = RgColors.TextSecondary,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(MAX_NAME)) },
            singleLine = true,
            placeholder = { Text("Tu nombre") },
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            modifier = Modifier.fillMaxWidth(),
        )
        RgPrimaryButton("Empezar", onConfirm, enabled = name.isNotBlank(), icon = Icons.Rounded.Check)
    }
}

private const val MAX_NAME = 40
