import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface TemplateRenderRequest {
  templateId: string;
  resumeData: Record<string, unknown>;
  customization?: Record<string, string>;
}

export interface TemplateRenderResponse {
  templateId: string;
  renderedHtml: string;
  success: boolean;
  errorMessage?: string;
}

/** Mirror of section-service ResumeData — all optional for partial usage */
export interface ResumeData {
  personalInfo?: Record<string, unknown>;
  summary?: string;
  experience?: Record<string, unknown>[];
  education?: Record<string, unknown>[];
  skills?: string[];
  certifications?: Record<string, unknown>[];
  projects?: Record<string, unknown>[];
}

@Injectable({ providedIn: 'root' })
export class TemplateEngineService {
  private readonly apiUrl = `${environment.apiBaseUrl}/api/v1/templates`;

  constructor(private readonly http: HttpClient) { }

  /**
   * Renders a template with the legacy flat map (existing usage — keep for backwards compat).
   */
  renderTemplate(request: TemplateRenderRequest): Observable<TemplateRenderResponse> {
    return this.http.post<TemplateRenderResponse>(`${this.apiUrl}/render`, request);
  }

  getTemplates(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  getTemplateById(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}`);
  }

  getPopularTemplates(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/popular`);
  }

  getCustomizationSchema(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}/customization-schema`);
  }

  /**
   * Renders a template with structured ResumeData DTO.
   * Returns the full standalone HTML string.
   * Used by the template gallery (sample data) and live editor (user data).
   * POST /api/v1/templates/{id}/render-data
   */
  renderWithData(templateId: string, data?: ResumeData): Observable<string> {
    return this.http.post(
      `${this.apiUrl}/${templateId}/render-data`,
      data ?? {},
      { responseType: 'text' }
    );
  }

  /**
   * Renders a template with REAL resume data fetched server-side from section-service.
   * POST /api/v1/templates/{templateId}/preview/{resumeId}
   */

  previewWithResumeId(templateId: string, resumeId: number): Observable<string> {
    return this.http.post(
      `${this.apiUrl}/${templateId}/preview/${resumeId}`,
      {},
      { responseType: 'text' }
    );
  }
}
