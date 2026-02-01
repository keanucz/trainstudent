package grandcentral

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"strings"
	"time"
)

var httpClient = &http.Client{Timeout: 30 * time.Second}

// StudentDiscountResult represents the result of student discount submission
type StudentDiscountResult struct {
	Status  string `json:"status"`
	Message string `json:"message"`
}

// IsAcUkEmail validates that the email is a .ac.uk address
func IsAcUkEmail(email string) bool {
	return strings.HasSuffix(strings.ToLower(strings.TrimSpace(email)), ".ac.uk")
}

// SubmitStudentDiscount submits the Grand Central student discount form
func SubmitStudentDiscount(ctx context.Context, firstName, lastName, email string) (*StudentDiscountResult, error) {
	if !IsAcUkEmail(email) {
		return nil, fmt.Errorf("email must be a .ac.uk address")
	}

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)

	fields := map[string]string{
		"SessionID": "", "CtcID": "", "OpCode": "60", "HasSinglePage": "true",
		"E-870": firstName, "E-871": lastName, "E-879": email,
		"C-890": "8992", "R-889": "8990", "ButtonSelected": "Submit",
	}

	for k, v := range fields {
		if err := writer.WriteField(k, v); err != nil {
			return nil, fmt.Errorf("failed to write field %s: %w", k, err)
		}
	}

	if err := writer.Close(); err != nil {
		return nil, fmt.Errorf("failed to close multipart writer: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST",
		"https://grandcentral.atreemosurvey.com/Home/SendSurveyData?id=7K20/EsmqMQhJ9GCBLVs9g==&newsession=1", &buf)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Accept", "application/json, text/javascript, */*; q=0.01")
	req.Header.Set("Accept-Language", "en-GB,en;q=0.9")
	req.Header.Set("Origin", "https://grandcentral.atreemosurvey.com")
	req.Header.Set("Referer", "https://grandcentral.atreemosurvey.com/")
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("X-Requested-With", "XMLHttpRequest")

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(body))
	}

	bodyStr := string(body)
	if strings.Contains(bodyStr, `"OpCode":"60"`) {
		return &StudentDiscountResult{
			Status:  "success",
			Message: fmt.Sprintf("Student discount verification sent to %s. Check your email.", email),
		}, nil
	}

	if strings.Contains(bodyStr, "error") || strings.Contains(bodyStr, "Error") {
		return nil, fmt.Errorf("submission failed: %s", bodyStr)
	}

	return &StudentDiscountResult{
		Status:  "pending",
		Message: "Submission received. Check your email for further instructions.",
	}, nil
}
