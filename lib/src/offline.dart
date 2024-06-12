// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

part of mapbox_gl;

final MethodChannel _offlineChannel = MethodChannel('plugins.flutter.io/offline_map');

/// Copy tiles db file passed in to the tiles cache directory (sideloaded) to
/// make tiles available offline.
void downloadOfflinePack({
  required String regionName,
  required LatLngBounds bounds,
  required double minZoom,
  required double maxZoom,
  required String styleUrl,
  Function? downloadProgressUpdate,
  Function? downloadProgressComplete,
  Function? downloadProgressError,
  Function? downloadProgressLimitExceeded,
}) async {
  _offlineChannel.setMethodCallHandler((MethodCall methodCall) {
    switch (methodCall.method) {
      case 'downloadProgressUpdate':
        if (downloadProgressUpdate != null) downloadProgressUpdate(methodCall.arguments);
        break;
      case 'downloadProgressComplete':
        if (downloadProgressComplete != null) downloadProgressComplete(methodCall.arguments);
        break;
      case 'downloadProgressError':
        if (downloadProgressError != null) downloadProgressError(methodCall.arguments);
        break;
      case 'downloadProgressLimitExceeded':
        if (downloadProgressLimitExceeded != null) downloadProgressLimitExceeded(methodCall.arguments);
        break;
    }
    return Future.value(true);
  });

  final Map<String, dynamic> args = <String, dynamic>{
    "regionName": regionName,
    "north": bounds.northeast.latitude,
    "east": bounds.northeast.longitude,
    "south": bounds.southwest.latitude,
    "west": bounds.southwest.longitude,
    "minZoom": minZoom,
    "maxZoom": maxZoom,
    "styleUrl": styleUrl,
  };

  await _offlineChannel.invokeMethod('offline#downloadOnClick', args);
}
