import type { Product } from '../../types';
import { LOW_STOCK_THRESHOLD, PRODUCT_STATUS_LABELS, PRODUCT_TYPE_LABELS } from '../inventory/productLabels';


interface ProductTableProps {
  products?: Product[];
  busyId?: string | null;
  onAdjustStock?: (product: Product, delta: number) => void;
  onEdit?: (product: Product) => void;
  onDelete?: (product: Product) => void;
}

const money = new Intl.NumberFormat('es-SV', { style: 'currency', currency: 'USD' });

export function ProductTable({ products = [], busyId, onAdjustStock, onEdit, onDelete }: ProductTableProps) {
  return (
    <div className="overflow-x-auto rounded-xl border border-gray-100 bg-white shadow-sm">
      <table className="w-full text-left text-xs border-collapse">
        <thead>
          <tr className="bg-gray-50 border-b border-gray-100 text-gray-700 font-semibold">
            <th className="p-2.5">Producto</th>
            <th className="p-2.5">Categoría</th>
            <th className="p-2.5">Precio</th>
            <th className="p-2.5">Stock</th>
            <th className="p-2.5">Estado</th>
            {(onEdit || onDelete) && <th className="p-2.5 text-right">Acciones</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {products.map((p) => {
            const stock = p.stock ?? 0;
            const busy = busyId === p.productId;
            return (
              <tr key={p.productId} className="hover:bg-pink-50/40 transition">
                <td className="p-2.5 font-bold text-gray-800">
                  {p.name}
                  {p.materials && <span className="block font-normal text-gray-400">{p.materials}</span>}
                </td>
                <td className="p-2.5 text-gray-500">{PRODUCT_TYPE_LABELS[p.type ?? ''] ?? p.type}</td>
                <td className="p-2.5 text-gray-700">{money.format(Number(p.price ?? 0))}</td>
                <td className="p-2.5">
                  <div className="flex items-center gap-1.5">
                    {onAdjustStock && (
                      <button
                        type="button"
                        aria-label={`Restar stock de ${p.name}`}
                        disabled={busy || stock === 0}
                        onClick={() => onAdjustStock(p, -1)}
                        className="w-6 h-6 rounded-lg bg-gray-100 hover:bg-gray-200 font-bold cursor-pointer disabled:opacity-40"
                      >
                        −
                      </button>
                    )}
                    <span className={`font-semibold min-w-6 text-center ${stock <= LOW_STOCK_THRESHOLD ? 'text-red-600' : 'text-gray-700'}`}>
                      {stock}
                    </span>
                    {onAdjustStock && (
                      <button
                        type="button"
                        aria-label={`Sumar stock de ${p.name}`}
                        disabled={busy}
                        onClick={() => onAdjustStock(p, 1)}
                        className="w-6 h-6 rounded-lg bg-pink-100 hover:bg-pink-200 text-pink-700 font-bold cursor-pointer disabled:opacity-40"
                      >
                        +
                      </button>
                    )}
                  </div>
                </td>
                <td className="p-2.5">
                  <span
                    className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                      p.status === 'AVAILABLE' ? 'bg-pink-100 text-pink-700' : 'bg-red-100 text-red-700'
                    }`}
                  >
                    {PRODUCT_STATUS_LABELS[p.status ?? ''] ?? p.status}
                  </span>
                </td>
                {(onEdit || onDelete) && (
                  <td className="p-2.5 text-right whitespace-nowrap">
                    {onEdit && (
                      <button type="button" title="Editar" onClick={() => onEdit(p)} className="p-1.5 bg-pink-50 hover:bg-pink-100 rounded-lg cursor-pointer">
                        ✏️
                      </button>
                    )}
                    {onDelete && (
                      <button type="button" title="Eliminar" onClick={() => onDelete(p)} className="p-1.5 ml-1 bg-red-50 hover:bg-red-100 rounded-lg cursor-pointer">
                        🗑️
                      </button>
                    )}
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default ProductTable;
