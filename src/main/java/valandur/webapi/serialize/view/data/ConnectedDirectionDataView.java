package valandur.webapi.serialize.view.data;

import com.fasterxml.jackson.annotation.JsonValue;
import org.spongepowered.api.data.manipulator.mutable.block.ConnectedDirectionData;
import org.spongepowered.api.util.Direction;
import valandur.webapi.api.serialize.BaseView;

import java.util.Set;

public class ConnectedDirectionDataView extends BaseView<ConnectedDirectionData> {

    @JsonValue
    public Set<Direction> directions;


    public ConnectedDirectionDataView(ConnectedDirectionData value) {
        super(value);

        this.directions = value.connectedDirections().get();
    }
}
