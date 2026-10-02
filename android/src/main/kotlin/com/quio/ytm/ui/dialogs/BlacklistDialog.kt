package com.quio.ytm.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.quio.ytm.data.local.entity.ArtistBlacklistEntity
import com.quio.ytm.ui.theme.BgSurface1
import com.quio.ytm.ui.theme.BgSurface2
import com.quio.ytm.ui.theme.BorderSubtle
import com.quio.ytm.ui.theme.StatusError
import com.quio.ytm.ui.theme.Strings
import com.quio.ytm.ui.theme.TextMuted
import com.quio.ytm.ui.theme.TextPrimary
import com.quio.ytm.ui.theme.TextSecondary
import com.quio.ytm.ui.theme.YoutubeRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlacklistDialog(
    blacklistedArtists: List<ArtistBlacklistEntity>,
    onAddArtist: (String) -> Unit,
    onRemoveArtist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newArtistName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(blacklistedArtists, searchQuery) {
        if (searchQuery.isBlank()) {
            blacklistedArtists
        } else {
            blacklistedArtists.filter {
                it.name.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BgSurface1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(YoutubeRed.copy(alpha = 0.15f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Blacklist",
                        tint = YoutubeRed,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = Strings.BlacklistTitle,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = Strings.BlacklistSubtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input field + Block Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newArtistName,
                        onValueChange = { newArtistName = it },
                        placeholder = {
                            Text(
                                "Nombre del artista...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YoutubeRed,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = YoutubeRed
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val trimmed = newArtistName.trim()
                            if (trimmed.isNotBlank()) {
                                onAddArtist(trimmed)
                                newArtistName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
                        shape = RoundedCornerShape(10.dp),
                        enabled = newArtistName.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Bloquear",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Search Filter (if > 3 artists)
                if (blacklistedArtists.size > 3) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Filtrar lista (${blacklistedArtists.size})...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YoutubeRed,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = YoutubeRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Artist List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (blacklistedArtists.isEmpty()) {
                                Strings.BlacklistEmpty
                            } else {
                                "No se encontraron resultados para \"$searchQuery\""
                            },
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredList, key = { it.id }) { artist ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BgSurface2),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artist.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Bloqueado • ${dateFormat.format(Date(artist.createdAt))}",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveArtist(artist.name) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = Strings.BtnUnblock,
                                            tint = StatusError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface2),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Listo",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
