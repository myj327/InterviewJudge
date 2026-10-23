package com.decade.doj.submission.review;

import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.dto.ReviewReport;

public interface ReviewProvider {

    ReviewReport review(ReviewEvidence evidence);
}
