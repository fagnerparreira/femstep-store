import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface AdminDashboard {
  totalProducts: number;
  activeProducts: number;
  lowStockVariants: number;
  totalOrders: number;
  pendingOrders: number;
  approvedRevenue: number;
}

export interface AdminVariant {
  id: number;
  sku: string;
  size: number;
  color: string;
  priceOverride?: number;
  stock: number;
  reserved: number;
  available: number;
  active: boolean;
}

export interface AdminVariantRequest {
  sku: string;
  size: number;
  color: string;
  priceOverride?: number;
}

export interface AdminProduct {
  id: number;
  name: string;
  slug: string;
  category: string;
  price: number;
  active: boolean;
  featured: boolean;
  variants: number;
  totalStock: number;
  reservedStock: number;
  inventory: AdminVariant[];
}

export interface AdminOrder {
  orderNumber: string;
  customerName: string;
  customerEmail: string;
  status: string;
  paymentStatus: string;
  total: number;
  createdAt: string;
}

export interface AdminProductRequest {
  name: string;
  slug: string;
  description: string;
  shortDescription?: string;
  price: number;
  categoryId: number;
  featured: boolean;
  material?: string;
  brand?: string;
  imageUrl: string;
  variants: Array<{ sku: string; size: number; color: string; stock: number }>;
}

export interface AdminProductDetails {
  id: number;
  name: string;
  slug: string;
  description: string;
  shortDescription?: string;
  price: number;
  categoryId: number;
  active: boolean;
  featured: boolean;
  material?: string;
  brand?: string;
}

export interface AdminProductImage {
  id: number;
  url: string;
  altText: string;
  displayOrder: number;
  mainImage: boolean;
}

export type AdminProductImageRequest = Omit<AdminProductImage, 'id'>;

export type AdminProductUpdateRequest = Omit<AdminProductDetails, 'id' | 'active'>;

export interface AdminCoupon {
  id: number;
  code: string;
  description?: string;
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  minimumOrderValue: number;
  maximumDiscountValue?: number;
  usageLimit?: number;
  usageLimitPerUser: number;
  uses: number;
  startsAt: string;
  endsAt: string;
  active: boolean;
}

export type AdminCouponRequest = Omit<AdminCoupon, 'id' | 'uses'>;

export interface AdminCategory {
  id: number;
  name: string;
  slug: string;
  description?: string;
  active: boolean;
  products: number;
}

export type AdminCategoryRequest = Omit<AdminCategory, 'id' | 'products'>;

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/admin';

  dashboard(): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>(`${this.apiUrl}/dashboard`);
  }

  products(): Observable<AdminProduct[]> {
    return this.http.get<AdminProduct[]>(`${this.apiUrl}/products`);
  }

  product(productId: number): Observable<AdminProductDetails> {
    return this.http.get<AdminProductDetails>(`${this.apiUrl}/products/${productId}`);
  }

  createProduct(request: AdminProductRequest): Observable<AdminProduct> {
    return this.http.post<AdminProduct>(`${this.apiUrl}/products`, request);
  }

  updateProduct(
    productId: number,
    request: AdminProductUpdateRequest
  ): Observable<AdminProduct> {
    return this.http.put<AdminProduct>(
      `${this.apiUrl}/products/${productId}`,
      request
    );
  }

  toggleProduct(productId: number, active: boolean): Observable<AdminProduct> {
    return this.http.patch<AdminProduct>(
      `${this.apiUrl}/products/${productId}/active`,
      { active }
    );
  }

  productImages(productId: number): Observable<AdminProductImage[]> {
    return this.http.get<AdminProductImage[]>(
      `${this.apiUrl}/products/${productId}/images`
    );
  }

  createProductImage(
    productId: number,
    request: AdminProductImageRequest
  ): Observable<AdminProductImage> {
    return this.http.post<AdminProductImage>(
      `${this.apiUrl}/products/${productId}/images`,
      request
    );
  }

  updateProductImage(
    imageId: number,
    request: AdminProductImageRequest
  ): Observable<AdminProductImage> {
    return this.http.put<AdminProductImage>(
      `${this.apiUrl}/images/${imageId}`,
      request
    );
  }

  deleteProductImage(imageId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/images/${imageId}`);
  }

  createVariant(
    productId: number,
    request: AdminVariantRequest & { stock: number }
  ): Observable<AdminVariant> {
    return this.http.post<AdminVariant>(
      `${this.apiUrl}/products/${productId}/variants`,
      request
    );
  }

  updateVariant(
    variantId: number,
    request: AdminVariantRequest
  ): Observable<AdminVariant> {
    return this.http.put<AdminVariant>(`${this.apiUrl}/variants/${variantId}`, request);
  }

  toggleVariant(variantId: number, active: boolean): Observable<AdminVariant> {
    return this.http.patch<AdminVariant>(
      `${this.apiUrl}/variants/${variantId}/active`,
      { active }
    );
  }

  adjustStock(variantId: number, stockQuantity: number, reason: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/variants/${variantId}/stock`, {
      stockQuantity,
      reason
    });
  }

  orders(): Observable<AdminOrder[]> {
    return this.http.get<AdminOrder[]>(`${this.apiUrl}/orders`);
  }

  advanceOrder(
    orderNumber: string,
    status: string,
    trackingCode?: string
  ): Observable<AdminOrder> {
    return this.http.patch<AdminOrder>(
      `${this.apiUrl}/orders/${orderNumber}/status`,
      { status, trackingCode }
    );
  }

  coupons(): Observable<AdminCoupon[]> {
    return this.http.get<AdminCoupon[]>(`${this.apiUrl}/coupons`);
  }

  createCoupon(request: AdminCouponRequest): Observable<AdminCoupon> {
    return this.http.post<AdminCoupon>(`${this.apiUrl}/coupons`, request);
  }

  updateCoupon(couponId: number, request: AdminCouponRequest): Observable<AdminCoupon> {
    return this.http.put<AdminCoupon>(`${this.apiUrl}/coupons/${couponId}`, request);
  }

  toggleCoupon(couponId: number, active: boolean): Observable<AdminCoupon> {
    return this.http.patch<AdminCoupon>(
      `${this.apiUrl}/coupons/${couponId}/active`,
      { active }
    );
  }

  categories(): Observable<AdminCategory[]> {
    return this.http.get<AdminCategory[]>(`${this.apiUrl}/categories`);
  }

  createCategory(request: AdminCategoryRequest): Observable<AdminCategory> {
    return this.http.post<AdminCategory>(`${this.apiUrl}/categories`, request);
  }

  updateCategory(
    categoryId: number,
    request: AdminCategoryRequest
  ): Observable<AdminCategory> {
    return this.http.put<AdminCategory>(
      `${this.apiUrl}/categories/${categoryId}`,
      request
    );
  }

  toggleCategory(categoryId: number, active: boolean): Observable<AdminCategory> {
    return this.http.patch<AdminCategory>(
      `${this.apiUrl}/categories/${categoryId}/active`,
      { active }
    );
  }
}
