package com.example.crudsqlite1;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador del historial: une cada RegistroSensor de Room con una fila
 * item_registro del RecyclerView.
 */
public class HistorialAdapter extends RecyclerView.Adapter<HistorialAdapter.RegistroViewHolder> {

    private List<RegistroSensor> registros = new ArrayList<>();

    /** Color de texto del tema, para devolverlo cuando la fila no esta en alerta. */
    private int colorNormal;

    private static final SimpleDateFormat FORMATO =
            new SimpleDateFormat("dd/MM/yyyy · HH:mm:ss", new Locale("es", "MX"));

    /** Guarda las referencias a las vistas de UNA fila, para no buscarlas cada vez. */
    public static class RegistroViewHolder extends RecyclerView.ViewHolder {
        TextView txtSensor, txtFechaHora, txtValor, txtAlerta;

        public RegistroViewHolder(@NonNull View itemView) {
            super(itemView);
            txtSensor = itemView.findViewById(R.id.txt_sensor);
            txtFechaHora = itemView.findViewById(R.id.txt_fecha_hora);
            txtValor = itemView.findViewById(R.id.txt_valor);
            txtAlerta = itemView.findViewById(R.id.txt_alerta);
        }
    }

    // Se llama una vez por fila VISIBLE, no una por registro de la base.
    @NonNull
    @Override
    public RegistroViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_registro, padre, false);
        RegistroViewHolder holder = new RegistroViewHolder(vista);
        if (colorNormal == 0) {
            // El color que pone el tema (claro u oscuro), para restaurarlo luego
            colorNormal = holder.txtValor.getCurrentTextColor();
        }
        return holder;
    }

    // Se llama cada vez que una fila reciclada va a mostrar OTRO registro.
    @Override
    public void onBindViewHolder(@NonNull RegistroViewHolder holder, int posicion) {
        RegistroSensor r = registros.get(posicion);

        holder.txtSensor.setText(nombreBonito(r.getTipoSensor()));
        holder.txtFechaHora.setText(FORMATO.format(new Date(r.getTimestamp())));
        holder.txtValor.setText(formatearValor(r.getTipoSensor(), r.getValorLectura()));

        /* Las filas se RECICLAN, asi que el color se asigna en los DOS casos.
           Si solo se pintara de rojo al haber alerta, una fila reutilizada
           seguiria roja al mostrar una lectura normal. */
        if (r.isEstadoAlerta()) {
            holder.txtAlerta.setVisibility(View.VISIBLE);
            holder.txtValor.setTextColor(Color.parseColor("#D32F2F"));
        } else {
            holder.txtAlerta.setVisibility(View.GONE);
            holder.txtValor.setTextColor(colorNormal);
        }
    }

    @Override
    public int getItemCount() {
        return registros.size();
    }

    /** Recibe la lista nueva que emitio Room y repinta la pantalla. */
    public void actualizar(List<RegistroSensor> nuevos) {
        this.registros = nuevos;
        notifyDataSetChanged();
    }

    private String nombreBonito(String tipo) {
        switch (tipo) {
            case "luz": return "Luz ambiental";
            case "temperatura": return "Temperatura";
            case "humedad": return "Humedad";
            case "distancia": return "Distancia";
            default: return tipo;
        }
    }

    private String formatearValor(String tipo, double valor) {
        switch (tipo) {
            case "luz": return String.format(Locale.US, "%.0f %%", valor);
            case "humedad": return String.format(Locale.US, "%.1f %%", valor);
            case "distancia": return String.format(Locale.US, "%.1f cm", valor);
            default: return String.format(Locale.US, "%.1f °C", valor);
        }
    }
}
