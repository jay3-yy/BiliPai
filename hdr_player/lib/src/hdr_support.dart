class HdrSupportResult {
  final bool displaySupported;
  final bool eglSupported;
  final String reason;
  final List<int> hdrTypes;

  const HdrSupportResult({
    required this.displaySupported,
    required this.eglSupported,
    required this.reason,
    required this.hdrTypes,
  });

  // Display capability决定是否尝试HDR；EGL能力在渲染阶段再验证。
  bool get isSupported => displaySupported;
  bool get isEglSupported => eglSupported;

  factory HdrSupportResult.fromMap(Map<dynamic, dynamic> map) {
    return HdrSupportResult(
      displaySupported: map['displaySupported'] == true,
      eglSupported: map['eglSupported'] == true,
      reason: (map['reason'] as String?) ?? '',
      hdrTypes: (map['hdrTypes'] as List?)?.map((e) => e as int).toList() ??
          const <int>[],
    );
  }
}
