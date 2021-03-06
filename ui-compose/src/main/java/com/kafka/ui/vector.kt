///*
// * Copyright 2019 Google LLC
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.kafka.ui
//
//import androidx.annotation.DrawableRes
//import androidx.compose.Composable
//import androidx.ui.core.Modifier
//import androidx.ui.core.paint
//import androidx.ui.foundation.Box
//import androidx.ui.graphics.Color
//import androidx.ui.graphics.ColorFilter
//import androidx.ui.graphics.vector.VectorPainter
//import androidx.ui.layout.preferredSize
//import androidx.ui.res.vectorResource
//import androidx.ui.unit.Dp
//
//@Composable
//fun VectorImage(
//    modifier: Modifier = Modifier.None,
//    @DrawableRes id: Int,
//    tint: Color = colors().onPrimary,
//    size: Dp? = null
//) {
//    val vector = vectorResource(id)
//    Box(
//        modifier = modifier.preferredSize(size ?: vector.defaultWidth, size ?: vector.defaultHeight)
//            .paint(
//                painter = VectorPainter(vector),
//                colorFilter = ColorFilter.tint(tint)
//            )
//    )
//}
