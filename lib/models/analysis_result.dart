
class AnalysisResult {
  final String psychology;
  final String intention;
  final List<ReplyOption> replies;
  final String? riskWarning;

  AnalysisResult({
    required this.psychology,
    required this.intention,
    required this.replies,
    this.riskWarning,
  });

  factory AnalysisResult.fromJson(Map<String, dynamic> json) {
    return AnalysisResult(
      psychology: json['psychology'] ?? '',
      intention: json['intention'] ?? '',
      riskWarning: json['risk_warning'],
      replies: (json['replies'] as List?)
          ?.map((e) => ReplyOption.fromJson(e))
          .toList() ?? [],
    );
  }
}

class ReplyOption {
  final String style;
  final String content;

  ReplyOption({required this.style, required this.content});

  factory ReplyOption.fromJson(Map<String, dynamic> json) {
    return ReplyOption(
      style: json['style'] ?? '',
      content: json['content'] ?? '',
    );
  }
}
